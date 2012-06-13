package se.arnetheduck.j2c.transform;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

public class TypeBindingHeaderWriter {
	private final IPath root;
	private final ITypeBinding type;
	private final Transformer ctx;

	private String lastAccess;

	protected final Set<ITypeBinding> hardDeps = new TreeSet<ITypeBinding>(
			new BindingComparator());
	protected final Set<ITypeBinding> softDeps = new TreeSet<ITypeBinding>(
			new BindingComparator());

	public TypeBindingHeaderWriter(IPath root, Transformer ctx,
			ITypeBinding type) {
		this.root = root;
		this.ctx = ctx;
		this.type = type;

		lastAccess = HeaderUtil.initialAccess(type);
		softDep(type);
	}

	public void write() throws Exception {
		writeType();

		if (!type.isInterface()) {
			StubWriter sw = new StubWriter(root, ctx, type);
			sw.write(false, false);
			if (TransformUtil.hasNatives(type)) {
				sw = new StubWriter(root, ctx, type);
				sw.write(true, false);
			}
		}

		for (ITypeBinding tb : softDeps) {
			ctx.softDep(tb);
		}
	}

	private void writeType() throws Exception {
		for (ITypeBinding nb : type.getDeclaredTypes()) {
			TypeBindingHeaderWriter hw = new TypeBindingHeaderWriter(root, ctx,
					nb);
			hw.write();
		}

		try {
			String body = getBody();

			PrintWriter pw = HeaderUtil.open(root, type, ctx, softDeps,
					hardDeps);

			pw.print(body);

			pw.close();
		} catch (Exception e) {
			throw new Error(e);
		}
	}

	private String getBody() {
		StringWriter sw = new StringWriter();
		PrintWriter out = new PrintWriter(sw);

		out.println("{");

		lastAccess = HeaderUtil.printSuper(out, type, lastAccess);
		lastAccess = HeaderUtil.printClassLiteral(out, lastAccess);

		for (IVariableBinding vb : type.getDeclaredFields()) {
			printField(out, vb);
		}

		out.println();

		Set<String> usings = new HashSet<String>();

		boolean hasConstructor = false;
		boolean hasEmptyConstructor = false;
		for (IMethodBinding mb : type.getDeclaredMethods()) {
			printMethod(out, type, mb, usings);

			hasConstructor |= mb.isConstructor();
			hasEmptyConstructor |= mb.isConstructor()
					&& mb.getParameterTypes().length == 0;
		}

		if (!hasEmptyConstructor) {
			if (!hasConstructor && !"protected:".equals(lastAccess)) {
				lastAccess = "protected:";
				out.println(lastAccess);
			} else if (!hasConstructor) {
				lastAccess = HeaderUtil.printAccess(out, Modifier.PUBLIC,
						lastAccess);
			}

			out.print(TransformUtil.indent(1));
			out.print(TransformUtil.name(type));
			out.println("();");

			out.print(TransformUtil.indent(1));
			out.print("void ");
			out.print(TransformUtil.CTOR);
			out.println("();");
		}

		makeBaseCalls(out, usings);

		lastAccess = HeaderUtil.printEnumMethods(out, type, softDeps,
				lastAccess);

		lastAccess = HeaderUtil.printClinit(out, lastAccess);
		lastAccess = HeaderUtil.printDtor(out, type, lastAccess);
		lastAccess = HeaderUtil.printGetClass(out, type, lastAccess);

		HeaderUtil.printStringOperator(out, type);

		out.println("};");

		TransformUtil.printStringSupport(type, out);

		out.close();

		return sw.toString();
	}

	private void printField(PrintWriter pw, IVariableBinding vb) {
		lastAccess = HeaderUtil.printAccess(pw, vb.getModifiers(), lastAccess);
		softDep(vb.getType());

		boolean asMethod = TransformUtil.asMethod(vb);
		if (asMethod) {
			pw.print(TransformUtil.indent(1));
			pw.print("static "
					+ TransformUtil.relativeCName(vb.getType(), type, true)
					+ " ");

			pw.print(TransformUtil.ref(vb.getType()));
			pw.print("&" + TransformUtil.name(vb));
			pw.println("();");

			lastAccess = HeaderUtil.printAccess(pw, Modifier.PRIVATE,
					lastAccess);
		}

		pw.print(TransformUtil.indent(1));

		Object cv = TransformUtil.constantValue(vb);
		pw.print(TransformUtil.fieldModifiers(type, vb.getModifiers(), true,
				cv != null));

		pw.print(TransformUtil.relativeCName(vb.getType(),
				vb.getDeclaringClass(), true));
		pw.print(" ");

		pw.print(TransformUtil.refName(vb));
		pw.print(asMethod ? "_" : "");

		if (cv != null) {
			pw.print(" = ");
			pw.print(cv);
		}

		pw.println(";");
	}

	private void printMethod(PrintWriter pw, ITypeBinding tb,
			IMethodBinding mb, Set<String> usings) {
		if (mb.isConstructor() && mb.getParameterTypes().length == 0
				&& Modifier.isPrivate(mb.getModifiers())) {
			lastAccess = "protected:";
			pw.println(lastAccess);
		} else {
			lastAccess = HeaderUtil.printAccess(pw, mb.getModifiers(),
					lastAccess);
		}

		if ((Modifier.isAbstract(mb.getModifiers()) || tb.isInterface())
				&& TransformUtil.baseHasSame(mb, tb, ctx)) {
			// Defining once more will lead to virtual inheritance issues
			pw.print(TransformUtil.indent(1));
			pw.print("/*");
			TransformUtil.printSignature(pw, tb, mb, softDeps, false);
			pw.println("; (already declared) */");
			return;
		}

		if (Modifier.isPrivate(mb.getModifiers())) {
			// Skip implementation details
			pw.print(TransformUtil.indent(1));
			pw.print("/*");
			TransformUtil.printSignature(pw, tb, mb, softDeps, false);
			pw.println("; (private) */");
			return;
		}

		pw.print(TransformUtil.indent(1));

		TransformUtil.printSignature(pw, tb, mb, softDeps, false);

		if (Modifier.isAbstract(mb.getModifiers())) {
			pw.print(" = 0");
		}

		if (mb.isConstructor()) {
			pw.println(";");

			lastAccess = HeaderUtil
					.printAccess(pw, Modifier.PUBLIC, lastAccess);

			pw.print(TransformUtil.indent(1));
			pw.print("void ");
			pw.print(TransformUtil.CTOR);
			TransformUtil.printParams(pw, tb, mb, softDeps);
		}

		pw.println(";");

		IMethodBinding mb2 = TransformUtil.getSuperMethod(mb);

		if (mb2 != null && TransformUtil.returnCovariant(mb, mb2)) {
			hardDep(mb.getReturnType());
		}

		TransformUtil.declareBridge(pw, tb, mb, softDeps);

		String using = TransformUtil.methodUsing(mb);
		if (using != null) {
			if (usings.add(using)) {
				pw.print(TransformUtil.indent(1));
				pw.println(using);
			}
		}
	}

	private void makeBaseCalls(PrintWriter pw, Set<String> usings) {
		if (Modifier.isAbstract(type.getModifiers()) || !type.isClass()) {
			return;
		}

		List<IMethodBinding> missing = HeaderUtil.baseCallMethods(type);

		for (IMethodBinding mb : missing) {
			lastAccess = HeaderUtil
					.printAccess(pw, Modifier.PUBLIC, lastAccess);

			pw.print(TransformUtil.indent(1));
			TransformUtil.printSuperCall(pw, type, mb, softDeps);

			String using = TransformUtil.methodUsing(mb, type);
			if (using != null) {
				if (usings.add(using)) {
					pw.println(TransformUtil.indent(1) + using);
				}
			}
		}
	}

	public void hardDep(ITypeBinding dep) {
		TransformUtil.addDep(dep, hardDeps);
		ctx.hardDep(dep);
	}

	public void softDep(ITypeBinding dep) {
		TransformUtil.addDep(dep, softDeps);
		ctx.softDep(dep);
	}
}
