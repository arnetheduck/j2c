package se.arnetheduck.j2c.transform;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Modifier;

public class Impl {
	private static final String JAVA_CAST_HPP = "/se/arnetheduck/j2c/resources/java_cast.hpp";
	private static final String NPC_HPP = "/se/arnetheduck/j2c/resources/npc.hpp";
	private static final String FINALLY_HPP = "/se/arnetheduck/j2c/resources/finally.hpp";
	private static final String SYNCHRONIZED_HPP = "/se/arnetheduck/j2c/resources/synchronized.hpp";

	private static final String i1 = TransformUtil.indent(1);

	private final ITypeBinding type;
	private final Transformer ctx;

	private final DepInfo deps;

	private final String qcname;

	private PrintWriter out;
	private boolean isNative;

	private final Map<String, List<IMethodBinding>> methods = new TreeMap<String, List<IMethodBinding>>();

	public Impl(Transformer ctx, ITypeBinding type, DepInfo deps) {
		this.ctx = ctx;
		this.type = type;
		this.deps = deps;

		qcname = CName.qualified(type, false);
	}

	public void write(IPath root, String body, String suffix, String cinit,
			String clinit, boolean fmod, boolean isNative) throws IOException {

		this.isNative = isNative;

		// Extras need to be collected first to get the deps
		String extras = getExtras();

		try {
			out = FileUtil.open(TransformUtil.implPath(root, type, suffix)
					.toFile());

			if (type.getJavaElement() != null) {
				println("// Generated from " + type.getJavaElement().getPath());
			} else {
				println("// Generated");
			}

			printIncludes(fmod);

			deps.printArrays(out);
			printJavaCast();
			printNpc();
			printFinally();
			printSynchronized();

			print(body);

			printClassLiteral();
			printClinit(cinit, clinit);

			print(extras);

			printDtor();
			printGetClass();
		} finally {
			if (out != null) {
				out.close();
				out = null;
			}
		}
	}

	private String getExtras() {
		StringWriter sw = new StringWriter();
		out = new PrintWriter(sw);

		printSuperCalls();
		printUnhide();
		printEnumMethods();

		out.close();
		out = null;
		return sw.toString();
	}

	private void printIncludes(boolean fmod) {
		Set<String> includes = new HashSet<String>();
		printlnd(TransformUtil.include(type), includes);
		println();

		for (ITypeBinding dep : deps.getHardDeps()) {
			if (dep.isEqualTo(type)) {
				continue;
			}

			if (dep.isNullType() || dep.isPrimitive()) {
				continue;
			}

			printlnd(TransformUtil.include(dep), includes);
		}

		if (fmod) {
			printlnd("#include <cmath>", includes);
		}

		if (includes.size() > 1) {
			println();
		}
	}

	private void printClinit(String cinit, String clinit) {
		if (isNative || !TypeUtil.isClassLike(type)) {
			return;
		}

		if (cinit == null && clinit == null
				&& !TransformUtil.same(type, Object.class)) {
			return;
		}

		println("void " + qcname + "::" + CName.STATIC_INIT + "()");
		println("{");

		if (cinit != null) {
			println("struct string_init_ {");
			println(i1 + "string_init_() {");
			print(cinit);
			println(i1 + "}");
			println("};");
			println();
			println(i1 + "static string_init_ string_init_instance;");
			println();
		}

		if (type.getSuperclass() != null) {
			println(i1 + "super::" + CName.STATIC_INIT + "();");
		}

		if (clinit != null) {
			println(i1 + "static bool in_cl_init = false;");
			println("struct clinit_ {");
			println(i1 + "clinit_() {");
			println(i1 + i1 + "in_cl_init = true;");
			print(clinit);
			println(i1 + "}");
			println("};");
			println();
			println(i1 + "if(!in_cl_init) {");
			println(i1 + i1 + "static clinit_ clinit_instance;");
			println(i1 + "}");
		}

		println("}");
		println();
	}

	private void printClassLiteral() {
		if (isNative) {
			return;
		}

		println("extern java::lang::Class* class_(const char16_t* c, int n);");
		println();
		if (TransformUtil.isPrimitiveArray(type)) {
			println("template<>");
		}
		println("java::lang::Class* " + qcname + "::class_()");
		println("{");
		println("    static ::java::lang::Class* c = ::class_(u\""
				+ type.getQualifiedName() + "\", "
				+ type.getQualifiedName().length() + ");");
		println("    return c;");

		println("}");
		println();
	}

	private void printGetClass() {
		if (isNative || !type.isClass() && !type.isEnum() && !type.isArray()) {
			return;
		}

		if (TransformUtil.isPrimitiveArray(type)) {
			println("template<>");
		}

		println("java::lang::Class* " + qcname + "::" + CName.GET_CLASS + "()");
		println("{");
		println(i1 + "return class_();");
		println("}");
		println();
	}

	private void printDtor() {
		if (isNative || !TransformUtil.same(type, Object.class)) {
			return;
		}
		println("java::lang::Object::~Object()");
		println("{");
		println("}");
		println();
	}

	/** Generate implicit enum methods */
	private void printEnumMethods() {
		if (!type.isEnum()) {
			return;
		}

		boolean hasValues = false;
		boolean hasValueOf = false;
		List<IMethodBinding> m = methods.get("values");
		if (m != null) {
			for (IMethodBinding mb : m) {
				hasValues |= TransformUtil.isValues(mb, type);
			}
		}

		m = methods.get("valueOf");
		if (m != null) {
			for (IMethodBinding mb : m) {
				hasValueOf |= TransformUtil.isValueOf(mb, type);
			}
		}

		for (IMethodBinding mb : type.getDeclaredMethods()) {
			if (!hasValues && TransformUtil.isValues(mb, type)) {
				deps.hard(type.createArrayType(1));
				TransformUtil.printSignature(ctx, out, type, mb, deps, true);
				println();
				println("{");
				println(i1 + "return new " + qcname + "Array({");
				for (IVariableBinding vb : type.getDeclaredFields()) {
					if (!vb.isEnumConstant())
						continue;
					println(i1 + i1 + CName.of(vb) + ",");
				}
				println(i1 + "});");
				println("}");
				println();
				hasValues = true;
			} else if (!hasValueOf && TransformUtil.isValueOf(mb, type)) {
				ITypeBinding iae = ctx.resolve(IllegalArgumentException.class);
				deps.hard(iae);
				TransformUtil.printSignature(ctx, out, type, mb, deps, true);
				println();
				println("{");
				String arg = TransformUtil.paramName(mb, 0);
				for (IVariableBinding vb : type.getDeclaredFields()) {
					if (!vb.isEnumConstant())
						continue;

					println(i1 + "if(" + CName.of(vb) + "->toString()->equals("
							+ arg + "))");
					println(i1 + i1 + "return " + CName.of(vb) + ";");
				}

				println(i1 + "throw new " + CName.relative(iae, type, true)
						+ "(" + arg + ");");
				println("}");
				println();
				hasValueOf = true;
			}
		}
	}

	private void printSuperCalls() {
		if (isNative || !TypeUtil.isClassLike(type)) {
			return;
		}

		List<IMethodBinding> missing = Header.baseCallMethods(type);
		for (IMethodBinding decl : missing) {
			IMethodBinding impl = Header.findImpl(type, decl);
			if (impl == null) {
				// Only print super call if an implementation actually
				// exists
				continue;
			}

			if (Modifier.isAbstract(impl.getModifiers())) {
				continue;
			}

			printSuperCall(decl, impl);
		}
	}

	private void printSuperCall(IMethodBinding decl, IMethodBinding impl) {
		IMethodBinding md = decl.getMethodDeclaration();
		TransformUtil.printSignature(ctx, out, type, md, impl.getReturnType(),
				deps, true);
		println();
		println("{");

		boolean erased = TransformUtil.returnErased(impl);
		method(decl);

		if (TransformUtil.isVoid(impl.getReturnType())) {
			out.format(i1 + "%s::%s(", CName.of(impl.getDeclaringClass()),
					CName.of(impl));
		} else {
			print(i1 + "return ");
			if (erased) {
				javaCast(impl.getMethodDeclaration().getReturnType()
						.getErasure(), impl.getReturnType());
			}

			out.format("%s::%s(", CName.of(impl.getDeclaringClass()),
					CName.of(impl));
		}

		String sep = "";
		for (int i = 0; i < impl.getParameterTypes().length; ++i) {
			print(sep);
			sep = ", ";

			boolean cast = !md.getParameterTypes()[i].getErasure()
					.isAssignmentCompatible(
							impl.getParameterTypes()[i].getErasure());
			if (cast) {
				javaCast(md.getParameterTypes()[i], impl.getParameterTypes()[i]);
			}
			print(TransformUtil.paramName(md, i));
			if (cast) {
				print(")");
			}
		}

		if (erased) {
			print(")");
		}

		println(");");
		println("}");
		println();
	}

	private void printUnhide() {
		if (isNative) {
			return;
		}

		List<IMethodBinding> superMethods = Header.hiddenMethods(type, ctx,
				methods);

		// The remaining methods need unhiding - we don't use "using" as it
		// breaks if there's a private method with the same name in the base
		// class
		for (IMethodBinding mb : superMethods) {
			if (Modifier.isAbstract(mb.getModifiers())) {
				continue;
			}

			mb = mb.getMethodDeclaration();

			ITypeBinding rt = mb.getReturnType();
			hardDep(rt);

			print(TransformUtil.qualifiedRef(rt, false));

			print(" ");

			print(qcname);
			print("::");

			print(CName.of(mb));

			TransformUtil.printParams(out, type, mb, true, deps);

			println();
			println("{");
			print(i1);
			if (!TransformUtil.isVoid(mb.getReturnType())) {
				hardDep(mb.getReturnType());
				print("return ");
			}

			if (type.isInterface()) {
				// This happens for the methods of Object for example
				print(CName.relative(mb.getDeclaringClass(), type, true) + "::");
			} else {
				print("super::");
			}

			print(CName.of(mb) + "(");

			String sep = "";
			for (int i = 0; i < mb.getParameterTypes().length; ++i) {
				print(sep + TransformUtil.paramName(mb, i));
				sep = ", ";
			}
			println(");");
			println("}");
			println();
		}
	}

	private void printJavaCast() {
		if (!deps.needsJavaCast()) {
			return;
		}

		print(FileUtil.readResource(JAVA_CAST_HPP));
	}

	private void printNpc() {
		if (!deps.needsNpc()) {
			return;
		}

		print(FileUtil.readResource(NPC_HPP));
	}

	private void printFinally() {
		if (!deps.needsFinally()) {
			return;
		}

		print(FileUtil.readResource(FINALLY_HPP));
	}

	private void printSynchronized() {
		if (!deps.needsSynchronized()) {
			return;
		}

		print(FileUtil.readResource(SYNCHRONIZED_HPP));
	}

	private void javaCast(ITypeBinding source, ITypeBinding target) {
		hardDep(source);
		hardDep(target);
		deps.setJavaCast();
		print(CName.JAVA_CAST + "< " + CName.relative(target, type, true)
				+ "* >(");
	}

	public void method(IMethodBinding mb) {
		if (mb.isConstructor()) {
			return;
		}

		List<IMethodBinding> m = methods.get(mb.getName());
		if (m == null) {
			methods.put(mb.getName(), m = new ArrayList<IMethodBinding>());
		}

		m.add(mb);
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

	public void printlnd(String s, Set<String> printed) {
		if (printed.add(s)) {
			println(s);
		}
	}
}
