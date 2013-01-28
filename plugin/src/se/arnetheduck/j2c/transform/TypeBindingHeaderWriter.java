package se.arnetheduck.j2c.transform;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

public class TypeBindingHeaderWriter {
	private final IPath root;
	private final ITypeBinding type;
	private final Transformer ctx;

	private final DepInfo deps;

	private String access;
	private final Header header;
	private static final String i1 = TransformUtil.indent(1);

	public TypeBindingHeaderWriter(IPath root, Transformer ctx,
			ITypeBinding type) {
		this.root = root;
		this.ctx = ctx;
		this.type = type;

		deps = new DepInfo(ctx);
		access = Header.initialAccess(type);
		softDep(type);
		header = new Header(ctx, type, deps);
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
					false, new ArrayList<ITypeBinding>(), access);
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
			printMethod(out, mb);
		}

		return sw.toString();
	}

	private void printField(PrintWriter pw, IVariableBinding vb) {
		ITypeBinding tb = vb.getType();
		softDep(tb);

		boolean asMethod = TransformUtil.asMethod(vb);
		int modifiers = vb.getModifiers();
		access = Header.printAccess(pw,
				asMethod ? Modifier.PRIVATE : modifiers, access);
		pw.print(TransformUtil.indent(1));

		Object cv = TransformUtil.constexprValue(vb);
		pw.print(TransformUtil.fieldModifiers(type, modifiers, true,
				cv != null));

		pw.print(TransformUtil.varTypeCName(modifiers, tb,
				vb.getDeclaringClass(), deps));
		pw.print(" ");

		pw.print(CName.of(vb));
		pw.print(asMethod ? "_" : "");

		String iv = TransformUtil.initialValue(vb);
		if (iv != null) {
			pw.print(" { " + iv + " }");
		}

		pw.println(";");
	}

	private void printMethod(PrintWriter pw, IMethodBinding mb) {
		if (TransformUtil.baseDeclared(ctx, type, mb)) {
			// Defining once more will lead to virtual inheritance issues
			pw.print(i1 + "/*");
			TransformUtil.printSignature(pw, type, mb, deps, false);
			pw.println("; (already declared) */");
			return;
		}

		if (Modifier.isPrivate(mb.getModifiers())) {
			// Skip implementation details
			pw.print(i1 + "/*");
			TransformUtil.printSignature(pw, type, mb, deps, false);
			pw.println("; (private) */");
			return;
		}

		if (mb.isConstructor()) {
			// The fake ctor should always be protected
			access = Header.printProtected(pw, access);
		} else {
			access = Header.printAccess(pw, mb.getModifiers(), access);
		}

		header.method(mb);

		pw.print(i1);
		TransformUtil.printSignature(pw, type, mb, deps, false);

		pw.print(TransformUtil.methodSpecifiers(mb));

		pw.println(";");

		for (ITypeBinding rd : TransformUtil.returnDeps(type, mb,
				ctx.resolve(Object.class))) {
			hardDep(rd);
		}
	}

	public void hardDep(ITypeBinding dep) {
		deps.hard(dep);
	}

	public void softDep(ITypeBinding dep) {
		deps.soft(dep);
	}
}
