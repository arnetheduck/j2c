package se.arnetheduck.j2c.transform;

import java.io.PrintWriter;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

public class TypeBindingHeaderWriter {
	private final IPath root;
	private final ITypeBinding type;
	private final Transformer ctx;

	private String lastAccess;

	public TypeBindingHeaderWriter(IPath root, Transformer ctx,
			ITypeBinding type) {
		this.root = root;
		this.ctx = ctx;
		this.type = type;
	}

	public void write() throws Exception {
		printClass();

		if (!type.isInterface()) {
			StubWriter sw = new StubWriter(root, ctx, type);
			sw.write(false, false);
			if (TransformUtil.hasNatives(type)) {
				sw = new StubWriter(root, ctx, type);
				sw.write(true, false);
			}
		}
	}

	private void printClass() throws Exception {
		ctx.headers.add(type);

		for (ITypeBinding nb : type.getDeclaredTypes()) {
			TypeBindingHeaderWriter hw = new TypeBindingHeaderWriter(root, ctx,
					nb);
			hw.write();
		}

		PrintWriter pw = TransformUtil.openHeader(root, type);

		List<ITypeBinding> bases = TransformUtil.getBases(type,
				ctx.resolve(Object.class));

		for (ITypeBinding b : bases) {
			pw.println(TransformUtil.include(b));
		}

		pw.println();

		if (type.isInterface()) {
			lastAccess = TransformUtil.PUBLIC;
			pw.print("struct ");
		} else {
			lastAccess = TransformUtil.PRIVATE;
			pw.print("class ");
		}

		pw.println(TransformUtil.qualifiedCName(type, false));

		String sep = ": public ";
		for (ITypeBinding b : bases) {
			ctx.hardDep(b);

			pw.print(TransformUtil.indent(1));
			pw.print(sep);
			sep = ", public ";
			pw.print(TransformUtil.virtual(b));
			pw.println(TransformUtil.relativeCName(b, type, true));
		}

		pw.println("{");

		if (type.getSuperclass() != null) {
			pw.print(TransformUtil.indent(1));
			pw.print("typedef ");
			pw.print(TransformUtil.relativeCName(type.getSuperclass(), type,
					true));
			pw.println(" super;");
		}

		lastAccess = TransformUtil.printAccess(pw, Modifier.PUBLIC, lastAccess);

		pw.print(TransformUtil.indent(1));
		pw.println(TransformUtil.CLASS_LITERAL);

		for (IVariableBinding vb : type.getDeclaredFields()) {
			lastAccess = TransformUtil.printAccess(pw, vb.getModifiers(),
					lastAccess);
			printField(pw, vb);
		}

		pw.println();

		Set<String> usings = new HashSet<String>();

		boolean hasEmptyConstructor = false;
		boolean hasConstructor = false;
		for (IMethodBinding mb : type.getDeclaredMethods()) {
			printMethod(pw, type, mb, usings);

			hasConstructor |= mb.isConstructor();
			hasEmptyConstructor |= mb.isConstructor()
					&& mb.getParameterTypes().length == 0;
		}

		if (!hasEmptyConstructor) {
			if (hasConstructor) {
				pw.println("protected:");
			}
			pw.print(TransformUtil.indent(1));
			pw.print(TransformUtil.name(type));
			pw.println("();");

			pw.print(TransformUtil.indent(1));
			pw.print("void ");
			pw.print(TransformUtil.CTOR);
			pw.println("();");
		}

		makeBaseCalls(pw);

		makeClinit(pw);

		makeDtor(pw);

		makeGetClass(pw);

		pw.println("};");

		TransformUtil.printStringSupport(type, pw);

		pw.close();
	}

	private void printField(PrintWriter pw, IVariableBinding vb) {
		ctx.softDep(vb.getType());

		boolean asMethod = TransformUtil.asMethod(vb);
		if (asMethod) {
			lastAccess = TransformUtil.printAccess(pw, vb.getModifiers(),
					lastAccess);

			pw.print(TransformUtil.indent(1));
			pw.print("static "
					+ TransformUtil.relativeCName(vb.getType(), type, true)
					+ " ");

			pw.print(TransformUtil.ref(vb.getType()));
			pw.print("&" + TransformUtil.name(vb));
			pw.println("();");

			lastAccess = TransformUtil.printAccess(pw, Modifier.PRIVATE,
					lastAccess);
		}

		pw.print(TransformUtil.indent(1));

		Object cv = TransformUtil.constantValue(vb);
		pw.print(TransformUtil.fieldModifiers(type, vb.getModifiers(), true,
				cv != null));

		pw.print(TransformUtil.relativeCName(vb.getType(),
				vb.getDeclaringClass(), true));
		pw.print(" ");

		pw.print(TransformUtil.ref(vb.getType()));
		pw.print(vb.getName());
		pw.print(asMethod ? "__" : "_");

		if (cv != null) {
			pw.print(" = ");
			pw.print(cv);
		}

		pw.println(";");
	}

	private void printMethod(PrintWriter pw, ITypeBinding tb,
			IMethodBinding mb, Set<String> usings) throws Exception {
		lastAccess = TransformUtil.printAccess(pw, mb.getModifiers(),
				lastAccess);
		if ((Modifier.isAbstract(mb.getModifiers()) || tb.isInterface())
				&& TransformUtil.baseHasSame(mb, tb, ctx)) {
			// Defining once more will lead to virtual inheritance issues
			pw.print(TransformUtil.indent(1));
			pw.print("/*");
			TransformUtil.printSignature(pw, tb, mb, ctx, false);
			pw.println("; (already declared) */");
			return;
		}

		if (Modifier.isPrivate(mb.getModifiers())) {
			// Skip implementation details
			pw.print(TransformUtil.indent(1));
			pw.print("/*");
			TransformUtil.printSignature(pw, tb, mb, ctx, false);
			pw.println("; (private) */");
			return;
		}

		pw.print(TransformUtil.indent(1));

		TransformUtil.printSignature(pw, tb, mb, ctx, false);

		if (Modifier.isAbstract(mb.getModifiers())) {
			pw.print(" = 0");
		}

		if (mb.isConstructor()) {
			pw.println(";");

			lastAccess = TransformUtil.printAccess(pw, Modifier.PUBLIC,
					lastAccess);

			pw.print(TransformUtil.indent(1));
			pw.print("void ");
			pw.print(TransformUtil.CTOR);
			TransformUtil.printParams(pw, tb, mb, ctx);
		}

		pw.println(";");

		TransformUtil.declareBridge(pw, tb, mb, ctx);

		String using = TransformUtil.methodUsing(mb);
		if (using != null) {
			if (usings.add(using)) {
				pw.print(TransformUtil.indent(1));
				pw.println(using);
			}
		}
	}

	private void makeBaseCalls(PrintWriter pw) {
		if (Modifier.isAbstract(type.getModifiers()) || !type.isClass()) {
			return;
		}

		List<IMethodBinding> missing = TransformUtil.baseCallMethods(type);

		for (IMethodBinding mb : missing) {
			lastAccess = TransformUtil.printAccess(pw, Modifier.PUBLIC,
					lastAccess);

			pw.print(TransformUtil.indent(1));
			TransformUtil.printSuperCall(pw, type, mb, ctx);
		}
	}

	private void makeClinit(PrintWriter pw) {
		if (!lastAccess.equals("protected:")) {
			lastAccess = "protected:";
			pw.println();
			pw.println(lastAccess);
		}

		pw.println(TransformUtil.indent(1) + TransformUtil.STATIC_INIT_DECL);
	}

	private void makeDtor(PrintWriter pw) {
		if (type.getQualifiedName().equals(Object.class.getName())) {
			lastAccess = TransformUtil.printAccess(pw, Modifier.PUBLIC,
					lastAccess);
			pw.print(TransformUtil.indent(1));
			pw.println("virtual ~Object();");
		}
	}

	private void makeGetClass(PrintWriter pw) {
		if (type.isClass()) {
			lastAccess = TransformUtil.printAccess(pw, Modifier.PRIVATE,
					lastAccess);
			pw.print(TransformUtil.indent(1));
			pw.println("virtual ::java::lang::Class* "
					+ TransformUtil.GET_CLASS + "();");
		}
	}
}
