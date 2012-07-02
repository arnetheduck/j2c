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

public class StubWriter {
	private final IPath root;
	private final ITypeBinding type;
	private final Transformer ctx;
	private Set<ITypeBinding> hardDeps = new TreeSet<ITypeBinding>(
			new BindingComparator());
	private Set<ITypeBinding> softDeps = new TreeSet<ITypeBinding>(
			new BindingComparator());

	private PrintWriter pw;

	public StubWriter(IPath root, Transformer ctx, ITypeBinding type) {
		if (type.isInterface()) {
			throw new UnsupportedOperationException();
		}

		this.root = root;
		this.ctx = ctx;
		this.type = type;
	}

	protected void hardDep(ITypeBinding dep) {
		TransformUtil.addDep(dep, hardDeps);
		ctx.hardDep(dep);
	}

	public void write(boolean natives, boolean privates) throws Exception {
		if (natives) {
			ctx.addNative(type);
			pw = TransformUtil.openImpl(root, type, TransformUtil.NATIVE);
		} else {
			ctx.addStub(type);
			pw = TransformUtil.openImpl(root, type, TransformUtil.STUB);
		}

		String body = body(natives, privates);

		for (ITypeBinding tb : hardDeps) {
			pw.println(TransformUtil.include(tb));
		}

		pw.print(body);

		pw.close();
	}

	public String body(boolean natives, boolean privates) throws Exception {
		PrintWriter old = pw;

		StringWriter ret = new StringWriter();
		pw = new PrintWriter(ret);

		if (!natives) {
			TransformUtil.printClassLiteral(pw, type);

			pw.println("void " + TransformUtil.qualifiedCName(type, false)
					+ "::" + TransformUtil.STATIC_INIT + "() { }");
			pw.println();

			makeGetClass();
			makeDtor();

			for (IVariableBinding vb : type.getDeclaredFields()) {
				printField(vb);
			}
		}

		pw.println();

		for (IMethodBinding mb : type.getDeclaredMethods()) {
			if (Modifier.isNative(mb.getModifiers()) == natives) {
				printMethod(mb, privates);
			}
		}

		pw.close();

		pw = old;

		return ret.toString();
	}

	private void makeGetClass() {
		if (type.isClass()) {
			TransformUtil.printGetClass(pw, type);
		}
	}

	private void makeDtor() {
		if (TransformUtil.same(type, Object.class)) {
			println("::java::lang::Object::~Object()");
			println("{");
			println("}");
			println("");
		}
	}

	private void printField(IVariableBinding vb) {
		if (!TransformUtil.isStatic(vb)) {
			return;
		}

		Object cv = TransformUtil.constantValue(vb);
		boolean asMethod = TransformUtil.asMethod(vb);

		if (asMethod) {
			ITypeBinding tb = vb.getType();
			print(TransformUtil.qualifiedCName(tb, true));

			print(" ");

			print(TransformUtil.ref(tb));

			print("&" + TransformUtil.qualifiedCName(type, false) + "::");

			print(TransformUtil.name(vb));

			pw.println("()");
			pw.println("{");
			pw.println(TransformUtil.indent(1) + TransformUtil.STATIC_INIT
					+ "();");
			pw.println(TransformUtil.indent(1) + "return "
					+ TransformUtil.name(vb) + "_;");
			println("}");
		}

		print(TransformUtil.fieldModifiers(type, vb.getModifiers(), false,
				cv != null));
		print(TransformUtil.qualifiedCName(vb.getType(), true));
		print(" ");

		print(TransformUtil.ref(vb.getType()));
		print(TransformUtil.qualifiedCName(vb.getDeclaringClass(), true));
		print("::");
		print(TransformUtil.name(vb));
		println(asMethod ? "_;" : ";");
	}

	private void printMethod(IMethodBinding mb, boolean privates)
			throws Exception {
		if (Modifier.isAbstract(mb.getModifiers())) {
			return;
		}

		if (Modifier.isPrivate(mb.getModifiers()) && !privates) {
			print("/* private: ");
			TransformUtil.printSignature(pw, type, mb, softDeps, true);
			println(" */");
			return;
		}

		if (!mb.isConstructor()) {
			ITypeBinding rt = mb.getReturnType();

			print(TransformUtil.qualifiedCName(rt, true));
			print(" ");
			print(TransformUtil.ref(rt));
		} else {
			print("void ");
			print(TransformUtil.qualifiedCName(type, true));
			print("::" + TransformUtil.CTOR);
			TransformUtil.printParams(pw, type, mb, true,
					new ArrayList<ITypeBinding>());
			println(" { }");
		}

		print(TransformUtil.qualifiedCName(type, true));
		print("::");

		print(mb.isConstructor() ? TransformUtil.name(type) : TransformUtil
				.name(mb));

		pw.print("(");

		String sep = mb.isConstructor() ? TransformUtil.printNestedParams(pw,
				type, new ArrayList<IVariableBinding>()) : "";

		if (mb.getParameterTypes().length > 0) {
			pw.print(sep);
			TransformUtil.printParams(pw, type, mb, false, softDeps);
		}

		pw.println(")");
		print("{");
		if (Modifier.isNative(mb.getModifiers())) {
			println(" /* native */");
		} else {
			println(" /* stub */");
		}

		if (TransformUtil.isStatic(mb)) {
			println(TransformUtil.indent(1) + TransformUtil.STATIC_INIT + "();");
		}

		if (mb.isConstructor()) {
			print(TransformUtil.indent(1) + TransformUtil.CTOR + "(");
			for (int i = 0; i < mb.getParameterTypes().length; ++i) {
				if (i > 0) {
					pw.print(", ");
				}

				pw.print(TransformUtil.paramName(mb, i));
			}
			pw.println(");");
		}

		boolean hasBody = false;
		for (Snippet snippet : ctx.snippets) {
			if (!snippet.body(ctx, this, mb)) {
				hasBody = true;
				break;
			}
		}

		if (!hasBody) {
			if (mb.getReturnType() != null
					&& !TransformUtil.isVoid(mb.getReturnType())) {
				print(TransformUtil.indent(1));
				println("return 0;");
			}
		}

		println("}");
		pw.println();

		for (ITypeBinding dep : TransformUtil.defineBridge(pw, type, mb,
				softDeps)) {
			hardDep(dep);
		}
	}

	public void print(String string) {
		pw.print(string);
	}

	public void println(String string) {
		pw.println(string);
	}
}
