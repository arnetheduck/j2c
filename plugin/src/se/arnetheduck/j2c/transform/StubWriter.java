package se.arnetheduck.j2c.transform;

import java.io.PrintWriter;
import java.lang.reflect.Modifier;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

public class StubWriter {
	private final IPath root;
	private final ITypeBinding type;
	private final Transformer ctx;

	public StubWriter(IPath root, Transformer ctx, ITypeBinding type) {
		if (type.isInterface()) {
			throw new UnsupportedOperationException();
		}

		this.root = root;
		this.ctx = ctx;
		this.type = type;
	}

	public void write() throws Exception {
		printClass(type);
	}

	private void printClass(ITypeBinding tb) throws Exception {
		ctx.stubs.add(tb);

		for (ITypeBinding nb : tb.getDeclaredTypes()) {
			ctx.hardDep(nb);
		}

		PrintWriter pw = TransformUtil.openImpl(root, tb);

		for (IVariableBinding vb : tb.getDeclaredFields()) {
			printField(pw, vb);
		}

		pw.println();

		for (IMethodBinding mb : tb.getDeclaredMethods()) {
			printMethod(pw, tb, mb);
		}

		printBridgeMethods(pw, tb);

		pw.close();
	}

	private void printField(PrintWriter pw, IVariableBinding vb) {
		if (!Modifier.isStatic(vb.getModifiers())) {
			return;
		}

		ctx.softDep(vb.getType());

		Object cv = TransformUtil.constantValue(vb);
		pw.print(TransformUtil.fieldModifiers(vb.getModifiers(), false,
				cv != null));
		pw.print(TransformUtil.qualifiedCName(vb.getType()));
		pw.print(" ");

		pw.print(TransformUtil.ref(vb.getType()));
		pw.print(TransformUtil.qualifiedCName(vb.getDeclaringClass()));
		pw.print("::");
		pw.print(vb.getName());
		pw.println("_;");
	}

	private void printMethod(PrintWriter pw, ITypeBinding tb, IMethodBinding mb)
			throws Exception {

		if (Modifier.isAbstract(mb.getModifiers())) {
			return;
		}

		if (Modifier.isPrivate(mb.getModifiers())) {
			pw.println("/* private: xxx " + mb.getName() + "(...) */");
			return;
		}

		if (!mb.isConstructor()) {
			ITypeBinding rt = mb.getReturnType();
			ctx.softDep(rt);

			pw.print(TransformUtil.qualifiedCName(rt));
			pw.print(" ");
			pw.print(TransformUtil.ref(rt));
		} else {
			pw.print("void ");
		}

		pw.print(TransformUtil.qualifiedCName(tb));
		pw.print("::");

		pw.print(mb.isConstructor() ? "_construct" : TransformUtil.keywords(mb
				.getMethodDeclaration().getName()));

		TransformUtil.printParams(pw, tb, mb, ctx);
		pw.println();
		pw.println("{");
		if (mb.getReturnType() != null
				&& !mb.getReturnType().getName().equals("void")) {
			pw.print(TransformUtil.indent(1));
			pw.println("return 0;");
		}
		pw.println("}");
		pw.println();
	}

	private void printBridgeMethods(PrintWriter pw, ITypeBinding tb)
			throws Exception {
		// TODO
	}
}
