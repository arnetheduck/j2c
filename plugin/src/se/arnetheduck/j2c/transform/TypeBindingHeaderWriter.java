package se.arnetheduck.j2c.transform;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
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

	private String access;
	private final Header header;

	protected final Set<ITypeBinding> hardDeps = new TreeSet<ITypeBinding>(
			new BindingComparator());
	protected final Set<ITypeBinding> softDeps = new TreeSet<ITypeBinding>(
			new BindingComparator());

	public TypeBindingHeaderWriter(IPath root, Transformer ctx,
			ITypeBinding type) {
		this.root = root;
		this.ctx = ctx;
		this.type = type;

		access = Header.initialAccess(type);
		softDep(type);
		header = new Header(ctx, type, softDeps, hardDeps);
	}

	public void write() throws Exception {
		writeType();

		if (!type.isInterface()) {
			StubWriter sw = new StubWriter(root, ctx, type);
			sw.write(false, false);
			if (hasNatives()) {
				sw = new StubWriter(root, ctx, type);
				sw.write(true, false);
			}
		}

		for (ITypeBinding tb : softDeps) {
			ctx.softDep(tb);
		}
	}

	private boolean hasNatives() {
		for (IMethodBinding mb : type.getDeclaredMethods()) {
			if (Modifier.isNative(mb.getModifiers())) {
				return true;
			}
		}

		return false;
	}

	private void writeType() throws Exception {
		for (ITypeBinding nb : type.getDeclaredTypes()) {
			TypeBindingHeaderWriter hw = new TypeBindingHeaderWriter(root, ctx,
					nb);
			hw.write();
		}

		try {
			String body = getBody();

			header.write(root, body, new ArrayList<IVariableBinding>(), false,
					new ArrayList<ITypeBinding>());
		} catch (Exception e) {
			throw new Error(e);
		}
	}

	private String getBody() {
		StringWriter sw = new StringWriter();
		PrintWriter out = new PrintWriter(sw);

		for (IVariableBinding vb : type.getDeclaredFields()) {
			header.field(vb);
			printField(out, vb);
		}

		out.println();

		for (IMethodBinding mb : type.getDeclaredMethods()) {
			printMethod(out, type, mb);
		}

		return sw.toString();
	}

	private void printField(PrintWriter pw, IVariableBinding vb) {
		softDep(vb.getType());

		boolean asMethod = TransformUtil.asMethod(vb);
		access = Header.printAccess(pw,
				asMethod ? Modifier.PRIVATE : vb.getModifiers(), access);
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

	private void printMethod(PrintWriter pw, ITypeBinding tb, IMethodBinding mb) {
		if (mb.isConstructor()) {
			// The fake ctor should always be protected
			access = Header.printProtected(pw, access);
		} else {
			access = Header.printAccess(pw, mb.getModifiers(), access);
		}

		if (Header.baseDeclared(ctx, type, mb)) {
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

		header.method(mb);

		pw.print(TransformUtil.indent(1));

		TransformUtil.printSignature(pw, tb, mb, softDeps, false);

		if (Modifier.isAbstract(mb.getModifiers())) {
			pw.print(" = 0");
		}

		pw.println(";");

		for (ITypeBinding rd : TransformUtil.returnDeps(type,
				ctx.resolve(Object.class), mb)) {
			hardDep(rd);
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
