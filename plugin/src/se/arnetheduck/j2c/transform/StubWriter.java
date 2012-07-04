package se.arnetheduck.j2c.transform;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

public class StubWriter {
	private static final String i1 = TransformUtil.indent(1);

	private final IPath root;
	private final Transformer ctx;
	private final ITypeBinding type;

	private final Set<ITypeBinding> hardDeps = new TreeSet<ITypeBinding>(
			new BindingComparator());
	private final Set<ITypeBinding> softDeps = new TreeSet<ITypeBinding>(
			new BindingComparator());

	private Collection<IMethodBinding> constructors = new ArrayList<IMethodBinding>();

	private final Impl impl;
	private final String qcname;
	private final String name;

	private PrintWriter pw;

	public StubWriter(IPath root, Transformer ctx, ITypeBinding type) {
		if (type.isInterface()) {
			throw new UnsupportedOperationException();
		}

		this.root = root;
		this.ctx = ctx;
		this.type = type;

		impl = new Impl(ctx, type, softDeps, hardDeps);
		qcname = TransformUtil.qualifiedCName(type, true);
		name = TransformUtil.name(type);
	}

	public void write(boolean natives, boolean privates) throws Exception {
		String body = getBody(natives, privates);
		String extras = getExtras(natives, privates);

		if (natives) {
			ctx.addNative(type);
			impl.write(root, extras + body, TransformUtil.NATIVE,
					new ArrayList<IVariableBinding>(), null, false, natives);
		} else {
			ctx.addStub(type);
			impl.write(root, extras + body, TransformUtil.STUB,
					new ArrayList<IVariableBinding>(), null, false, natives);
		}
	}

	private String getExtras(boolean natives, boolean privates)
			throws IOException {
		StringWriter sw = new StringWriter();
		pw = new PrintWriter(sw);

		// printFinally();
		// printSynchronized();

		if (!natives) {
			printConstructors();
		}

		if (!(type.isInterface() || type.isAnnotation())) {
			// printInit();
		}

		pw.close();
		pw = null;

		return sw.toString();
	}

	private String getBody(boolean natives, boolean privates) throws Exception {
		StringWriter ret = new StringWriter();
		pw = new PrintWriter(ret);

		if (!natives) {
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

		pw = null;

		return ret.toString();
	}

	private void printConstructors() {
		if (!type.isClass() && !type.isEnum()) {
			return;
		}

		if (type.isAnonymous()) {
			Header.getBaseConstructors(type, constructors);
		}

		boolean hasEmpty = false;
		for (IMethodBinding mb : constructors) {
			pw.print(qcname + "::" + name + "(");

			String sep = TransformUtil.printNestedParams(pw, type,
					new ArrayList<IVariableBinding>());

			if (mb.getParameterTypes().length > 0) {
				print(sep);

				TransformUtil.printParams(pw, type, mb, false, softDeps);
			} else {
				hasEmpty = true;
			}

			println(")");

			printFieldInit(": ");

			println("{");
			pw.println(i1 + TransformUtil.STATIC_INIT + "();");

			if (!type.isAnonymous()) {
				// Anonymous types don't have their own constructors
				pw.print(i1 + TransformUtil.CTOR + "(");

				sep = "";
				for (int i = 0; i < mb.getParameterTypes().length; ++i) {
					pw.print(sep + TransformUtil.paramName(mb, i));
					sep = ", ";
				}

				println(");");
			}

			pw.println("}");
			pw.println();
		}

		if (!hasEmpty) {
			printEmptyConstructor();
		}
	}

	private void printEmptyConstructor() {
		pw.print(qcname + "::" + name + "(");

		TransformUtil.printNestedParams(pw, type,
				new ArrayList<IVariableBinding>());

		println(")");

		printFieldInit(": ");
		println("{");

		pw.println(i1 + TransformUtil.STATIC_INIT + "();");

		pw.println("}");
		pw.println();

		println("void " + qcname + "::" + TransformUtil.CTOR + "()");
		println("{");
		println("}");
		println();
	}

	private void printFieldInit(String sep) {
		ITypeBinding sb = type.getSuperclass();
		if (sb != null && TransformUtil.hasOuterThis(sb)) {
			pw.print(i1 + sep);
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

			pw.print(i1 + sep + TransformUtil.name(vb));

			println("()");
			sep = ", ";
		}
	}

	private void printInit(String n) {
		pw.println(n + "(" + n + ")");
	}

	private void printField(IVariableBinding vb) {
		if (!TransformUtil.isStatic(vb)) {
			return;
		}

		Object cv = TransformUtil.constantValue(vb);
		boolean asMethod = TransformUtil.asMethod(vb);

		ITypeBinding vt = vb.getType();
		String vname = TransformUtil.name(vb);
		if (asMethod) {
			print(TransformUtil.qualifiedCName(vt, true));

			print(" ");

			print(TransformUtil.ref(vt));

			print("&" + qcname + "::");

			print(vname);

			pw.println("()");
			pw.println("{");
			pw.println(TransformUtil.indent(1) + TransformUtil.STATIC_INIT
					+ "();");
			pw.println(TransformUtil.indent(1) + "return " + vname + "_;");
			println("}");
		}

		print(TransformUtil.fieldModifiers(type, vb.getModifiers(), false,
				cv != null));
		print(TransformUtil.qualifiedCName(vt, true));
		print(" ");

		print(TransformUtil.ref(vt));
		print(qcname + "::" + vname);
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

		if (mb.isConstructor()) {
			constructors.add(mb);
		}

		TransformUtil.printSignature(pw, type, mb, softDeps, true);

		pw.println();
		print("{");
		if (Modifier.isNative(mb.getModifiers())) {
			println(" /* native */");
		} else {
			println(" /* stub */");
		}

		if (TransformUtil.isStatic(mb)) {
			println(i1 + TransformUtil.STATIC_INIT + "();");
		}

		boolean hasBody = false;
		for (Snippet snippet : ctx.snippets) {
			if (!snippet.body(ctx, this, mb)) {
				hasBody = true;
				break;
			}
		}

		if (!hasBody) {
			if (!TransformUtil.isVoid(mb.getReturnType())) {
				println(i1 + "return 0;");
			}
		}

		println("}");
		pw.println();

		for (ITypeBinding dep : TransformUtil.defineBridge(pw, type, mb,
				softDeps)) {
			hardDep(dep);
		}
	}

	private void hardDep(ITypeBinding dep) {
		TransformUtil.addDep(dep, hardDeps);
		ctx.hardDep(dep);
	}

	public void print(String string) {
		pw.print(string);
	}

	public void println(String string) {
		pw.println(string);
	}

	public void println() {
		pw.println();
	}
}
