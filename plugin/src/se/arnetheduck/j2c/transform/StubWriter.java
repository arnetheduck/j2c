package se.arnetheduck.j2c.transform;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

public class StubWriter {
	private static final String i1 = TransformUtil.indent(1);

	private final IPath root;
	private final Transformer ctx;
	private final ITypeBinding type;

	private final DepInfo deps;

	private Collection<IMethodBinding> constructors = new ArrayList<IMethodBinding>();

	private final Impl impl;
	private final String qcname;
	private final String name;

	private PrintWriter out;

	public StubWriter(IPath root, Transformer ctx, ITypeBinding type) {
		if (type.isInterface()) {
			throw new UnsupportedOperationException();
		}

		this.root = root;
		this.ctx = ctx;
		this.type = type;

		deps = new DepInfo(ctx);
		impl = new Impl(ctx, type, deps);
		qcname = CName.qualified(type, true);
		name = CName.of(type);
	}

	public void write(boolean natives, boolean privates) throws Exception {
		String body = getBody(natives, privates);
		String extras = getExtras(natives, privates);

		if (natives) {
			ctx.addNative(type);
			impl.write(root, extras + body, TransformUtil.NATIVE,
					new ArrayList<IVariableBinding>(), null, null, false,
					natives);
		} else {
			ctx.addStub(type);
			impl.write(root, extras + body, TransformUtil.STUB,
					new ArrayList<IVariableBinding>(), null, null, false,
					natives);
		}
	}

	private String getExtras(boolean natives, boolean privates)
			throws IOException {
		StringWriter sw = new StringWriter();
		out = new PrintWriter(sw);

		out.println("extern void unimplemented_(const char16_t* name);");

		if (!natives) {
			printDefaultInitCtor();
			printCtors();
		}

		out.close();
		out = null;

		return sw.toString();
	}

	private String getBody(boolean natives, boolean privates) throws Exception {
		StringWriter ret = new StringWriter();
		out = new PrintWriter(ret);

		if (!natives) {
			for (IVariableBinding vb : type.getDeclaredFields()) {
				printField(vb);
			}
		}

		println();

		for (IMethodBinding mb : type.getDeclaredMethods()) {
			if (Modifier.isNative(mb.getModifiers()) == natives) {
				printMethod(mb, privates);
			}
		}

		out.close();

		out = null;

		return ret.toString();
	}

	private void printCtors() {
		if (!TypeUtil.isClassLike(type)) {
			return;
		}

		if (type.isAnonymous()) {
			Header.getAnonCtors(type, constructors);
		}

		boolean hasEmpty = false;
		for (IMethodBinding mb : constructors) {
			print(qcname + "::" + name + "(");

			String sep = TransformUtil.printNestedParams(out, type,
					new ArrayList<IVariableBinding>());

			if (mb.getParameterTypes().length > 0) {
				print(sep);
				TransformUtil.printParams(out, type, mb, false, deps);
			} else {
				hasEmpty = true;
			}

			println(")");

			print(i1 + ": " + name + "(");
			sep = "";
			if (TransformUtil.hasOuterThis(type)) {
				print(TransformUtil.outerThisName(type));
				sep = ", ";
			}

			println(sep + "*static_cast< ::" + CName.DEFAULT_INIT_TAG
					+ "* >(0))");

			println("{");
			print(i1 + CName.CTOR + "(");

			sep = "";
			for (int i = 0; i < mb.getParameterTypes().length; ++i) {
				print(sep + TransformUtil.paramName(mb, i));
				sep = ", ";
			}

			println(");");

			println("}");
			println();
		}

		if (!hasEmpty) {
			printEmptyCtor();
		}
	}

	private void printDefaultInitCtor() {
		if (!TypeUtil.isClassLike(type) || type.isAnonymous()) {
			return;
		}

		print(qcname + "::" + name + "(");
		print(TransformUtil.printNestedParams(out, type, null));
		println("const ::" + CName.DEFAULT_INIT_TAG + "&)");

		printFieldInit(": ");

		println("{");
		println(i1 + CName.STATIC_INIT + "();");
		println("}");
		println();

	}

	private void printEmptyCtor() {
		if (type.isAnonymous()) {
			return;
		}

		println("void " + qcname + "::" + CName.CTOR + "()");
		println("{");
		if (type.getSuperclass() != null) {
			println(i1 + "super::" + CName.CTOR + "();");
		}

		println("}");
		println();
	}

	private void printFieldInit(String sep) {
		ITypeBinding sb = type.getSuperclass();
		if (sb != null && TransformUtil.hasOuterThis(sb)) {
			print(i1 + sep);
			print("super(");
			String sepx = "";
			for (ITypeBinding tb = type; tb.getDeclaringClass() != null; tb = tb
					.getDeclaringClass().getErasure()) {
				print(sepx);
				sepx = "->";
				hardDep(tb.getDeclaringClass());
				print(TransformUtil.outerThisName(tb));
				if (tb.getDeclaringClass()
						.getErasure()
						.isSubTypeCompatible(
								sb.getDeclaringClass().getErasure())) {
					break;
				}

			}

			println(")");
			sep = ", ";
		}

		if (TransformUtil.hasOuterThis(type)
				&& (sb == null || sb.getDeclaringClass() == null || !type
						.getDeclaringClass().getErasure()
						.isEqualTo(sb.getDeclaringClass().getErasure()))) {
			print(i1 + sep);
			hardDep(type.getDeclaringClass());
			printInit(TransformUtil.outerThisName(type));
			sep = ", ";
		}

		for (IVariableBinding vb : type.getDeclaredFields()) {
			if (TransformUtil.isStatic(vb)) {
				continue;
			}

			if (TransformUtil.constantValue(vb) != null) {
				continue;
			}

			print(i1 + sep + CName.of(vb));

			println("()");
			sep = ", ";
		}
	}

	private void printInit(String n) {
		println(n + "(" + n + ")");
	}

	private void printField(IVariableBinding vb) {
		if (!TransformUtil.isStatic(vb)) {
			return;
		}

		Object cv = TransformUtil.constexprValue(vb);
		boolean asMethod = TransformUtil.asMethod(vb);

		ITypeBinding vt = vb.getType();
		String vname = CName.of(vb);
		String qvtname = CName.qualified(vt, true);
		if (asMethod) {
			print(qvtname + " " + TransformUtil.ref(vt));
			println("&" + qcname + "::" + vname + "()");
			println("{");
			println(i1 + CName.STATIC_INIT + "();");
			println(i1 + "return " + vname + "_;");
			println("}");
		}

		print(TransformUtil.fieldModifiers(type, vb.getModifiers(), false,
				cv != null && !(cv instanceof String)));

		print(qvtname + " " + TransformUtil.ref(vt) + qcname + "::" + vname);
		println(asMethod ? "_;" : ";");
	}

	private void printMethod(IMethodBinding mb, boolean privates)
			throws Exception {
		if (Modifier.isAbstract(mb.getModifiers())) {
			return;
		}

		if (Modifier.isPrivate(mb.getModifiers()) && !privates) {
			print("/* private: ");
			TransformUtil.printSignature(out, type, mb, deps, true);
			println(" */");
			return;
		}

		impl.method(mb);

		if (mb.isConstructor()) {
			constructors.add(mb);
		}

		TransformUtil.printSignature(out, type, mb, deps, true);

		println();
		print("{");
		if (Modifier.isNative(mb.getModifiers())) {
			println(" /* native */");
		} else {
			println(" /* stub */");
		}

		if (TransformUtil.isStatic(mb)) {
			println(i1 + CName.STATIC_INIT + "();");
		}

		if (mb.isConstructor() && type.getSuperclass() != null) {
			println(i1 + "super::" + CName.CTOR + "();");
		}

		boolean hasBody = false;
		for (Snippet snippet : ctx.snippets) {
			if (!snippet.body(ctx, this, mb)) {
				hasBody = true;
				break;
			}
		}

		if (!hasBody) {
			print(i1 + "unimplemented_(u\"");
			TransformUtil.printSignature(out, type, mb, deps, true);
			println("\");");
			if (!TransformUtil.isVoid(mb.getReturnType())) {
				println(i1 + "return 0;");
			}
		}

		println("}");
		println();

		TransformUtil.defineBridge(out, type, mb, deps);
	}

	private void hardDep(ITypeBinding dep) {
		deps.hard(dep);
	}

	public void print(String string) {
		out.print(string);
	}

	public void println(String string) {
		out.println(string);
	}

	public void println() {
		out.println();
	}
}
