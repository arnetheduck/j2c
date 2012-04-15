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
	private PrintWriter pw;

	public StubWriter(IPath root, Transformer ctx, ITypeBinding type) {
		if (type.isInterface()) {
			throw new UnsupportedOperationException();
		}

		this.root = root;
		this.ctx = ctx;
		this.type = type;
	}

	public void write(boolean natives) throws Exception {
		for (ITypeBinding nb : type.getDeclaredTypes()) {
			ctx.hardDep(nb);
		}

		if (natives) {
			ctx.natives.add(type);
			pw = TransformUtil.openImpl(root, type, TransformUtil.NATIVE);
		} else {
			ctx.stubs.add(type);
			pw = TransformUtil.openImpl(root, type, TransformUtil.STUB);
		}

		for (IVariableBinding vb : type.getDeclaredFields()) {
			printField(pw, vb);
		}

		pw.println();

		for (IMethodBinding mb : type.getDeclaredMethods()) {
			if (Modifier.isNative(mb.getModifiers()) == natives) {
				printMethod(pw, type, mb);
			}
		}

		pw.close();
	}

	private void printField(PrintWriter pw, IVariableBinding vb) {
		if (!TransformUtil.isStatic(vb)) {
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
		pw.print("{");
		if (Modifier.isNative(mb.getModifiers())) {
			pw.print(" /* native */");
		} else {
			pw.print(" /* stub */");
		}

		pw.println();
		boolean hasBody = false;
		for (Snippet snippet : ctx.snippets) {
			if (!snippet.body(ctx, this, mb)) {
				hasBody = true;
				break;
			}
		}

		if (!hasBody) {
			if (mb.getReturnType() != null
					&& !mb.getReturnType().getName().equals("void")) {
				pw.print(TransformUtil.indent(1));
				pw.println("return 0;");
			}
		}

		pw.println("}");
		pw.println();

		for (ITypeBinding dep : TransformUtil.defineBridge(pw, type, mb, ctx)) {
			ctx.hardDep(dep);
		}
	}

	public void print(String string) {
		pw.println(string);
	}

	public void println(String string) {
		pw.println(string);
	}
}
