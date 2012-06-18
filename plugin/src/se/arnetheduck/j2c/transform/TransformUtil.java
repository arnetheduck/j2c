package se.arnetheduck.j2c.transform;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Modifier;
import java.text.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IInitializer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

public final class TransformUtil {
	public static final String NATIVE = "-native";
	public static final String STUB = "-stub";

	/** Name of fake static initializer */
	public static final String STATIC_INIT = "clinit";
	/** Name of fake instance initializer */
	public static final String INSTANCE_INIT = "init";

	/** Name of fake constructor */
	public static final String CTOR = "ctor";

	/** Virtual method that returns the dynamic class of the current object */
	public static final String GET_CLASS = "getClass0";

	/** C++ keywords + special method names - java keywords */
	private static Collection<String> keywords = Arrays.asList("alignas",
			"alignof", "and", "and_eq", "asm", "auto", "bitand", "bitor",
			"bool", "char16_t", "char32_t", "compl", "const", "constexpre",
			"const_cast", "decltype", "delete", "dynamic_cast", "explicit",
			"export", "extern", "friend", "goto", "inline", "mutable",
			"namespace", "noexcept", "not", "not_eq", "nullptr", "operator",
			"or", "or_eq", "register", "reinterpret_cast", "signed", "sizeof",
			"static_assert", "static_cast", "struct", "template",
			"thread_local", "typedef", "typeid", "typename", "union",
			"unsigned", "using", "wchar_t", "xor", "xor_eq", CTOR,
			INSTANCE_INIT, STATIC_INIT, GET_CLASS, "int8_t", "int16_t",
			"int32_t", "int64_t");

	public static final Map<String, String> primitives = new HashMap<String, String>() {
		{
			put("boolean", "java.lang.Boolean");
			put("char", "java.lang.Character");
			put("byte", "java.lang.Byte");
			put("short", "java.lang.Short");
			put("int", "java.lang.Integer");
			put("long", "java.lang.Long");
			put("float", "java.lang.Float");
			put("double", "java.lang.Double");
		}
	};

	public static final Map<String, String> reverses = new HashMap<String, String>() {
		{
			for (Map.Entry<String, String> x : primitives.entrySet()) {
				put(x.getValue(), x.getKey());
			}
		}
	};

	public static String cname(String jname) {
		return jname.replace(".", "::");
	}

	public static String qualifiedCName(ITypeBinding tb, boolean global) {
		IPackageBinding pkg = elementPackage(tb);
		return (global && !tb.isPrimitive() ? "::" : "")
				+ cname(pkg == null ? name(tb) : (name(pkg) + "." + name(tb)));
	}

	public static String relativeCName(ITypeBinding tb, ITypeBinding root,
			boolean global) {
		if (!samePackage(tb, root)) {
			return qualifiedCName(tb, global);
		}

		String tbn = name(tb);

		// In C++, unqualified names in a class are looked up in base
		// classes before the own namespace
		for (ITypeBinding sb = root.getSuperclass(); sb != null; sb = sb
				.getSuperclass()) {
			if (samePackage(tb, sb)) {
				return name(tb);
			}

			if (name(sb).equals(tbn)) {
				return qualifiedCName(tb, global);
			}
		}

		if (tbn.equals(Object.class.getSimpleName()) && !same(tb, Object.class)) {
			// Intefaces have null superclass but inherit (conceptually) from
			// Object
			return qualifiedCName(tb, global);
		}

		return tbn;
	}

	private static IPackageBinding elementPackage(ITypeBinding tb) {
		// When processing generics, only the erasure will have a package
		return tb.isArray() ? tb.getElementType().getErasure().getPackage()
				: tb.getErasure().getPackage();
	}

	private static boolean samePackage(ITypeBinding tb0, ITypeBinding tb1) {
		IPackageBinding p0 = elementPackage(tb0);
		IPackageBinding p1 = elementPackage(tb1);
		if (p0 == null) {
			return p1 == null;
		}

		return p1 != null && p0.isEqualTo(p1);
	}

	private static Pattern lastBin = Pattern.compile("\\$(\\d*)$");

	public static String name(ITypeBinding tb) {
		if (tb.isArray()) {
			return name(tb.getComponentType()) + "Array";
		}

		if (tb.isPrimitive()) {
			return primitive(tb.getName());
		}

		ITypeBinding tbe = tb.getErasure();

		if (tbe.isLocal()) {
			Matcher match = lastBin.matcher(tbe.getBinaryName());
			String extra = match.find() ? match.group(1) : "";
			String c = tbe.getDeclaringClass() == null ? "c" : name(tbe
					.getDeclaringClass());
			String m = tbe.getDeclaringMethod() == null ? "m" : tbe
					.getDeclaringMethod().getName();
			return c + "_" + m + extra;
		}

		if (tbe.isNested()) {
			return name(tbe.getDeclaringClass()) + "_" + tbe.getName();
		}

		return keywords(tbe.getName());
	}

	public static String name(IMethodBinding mb) {
		// private methods mess up using statements that import methods
		// from base classes
		String ret = keywords(mb.getName());
		ret = Modifier.isPrivate(mb.getModifiers()) ? "_" + ret : ret;

		if (couldOverrideDefault(mb, ret)) {
			ret = name(mb.getDeclaringClass()) + "_" + ret;
		}

		// Methods can have the same name as the constructor without being a
		// constructor!
		if (!mb.isConstructor() && ret.equals(name(mb.getDeclaringClass()))) {
			ret = "_" + ret;
		}

		return ret;
	}

	/**
	 * In Java, if a method has the same name as a package-private method in a
	 * base class, it will not override the base class member. In C++, we have
	 * nothing of the sort so we have to rename the non-overriding method
	 */
	public static boolean couldOverrideDefault(IMethodBinding mb, String ret) {
		ITypeBinding tb = mb.getDeclaringClass();
		if (tb != null) {
			for (ITypeBinding tbx = tb.getSuperclass(); tbx != null; tbx = tbx
					.getSuperclass()) {
				if (samePackage(tb, tbx)) {
					continue;
				}

				for (IMethodBinding mbx : tbx.getDeclaredMethods()) {
					if (isDefault(mbx.getModifiers())
							&& mbx.getName().equals(mb.getName())
							&& sameParameters(mb, mbx)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	public static String name(IVariableBinding vb) {
		// Methods and variables can have the same name so we postfix all
		// variables
		return vb.getName() + "_";
	}

	public static String name(IPackageBinding pb) {
		String[] n = pb.getNameComponents();
		StringBuilder ret = new StringBuilder();
		String sep = "";
		for (int i = 0; i < n.length; ++i) {
			ret.append(sep);
			sep = ".";
			ret.append(keywords(n[i]));
		}

		return ret.toString();
	}

	public static String packageName(ITypeBinding tb) {
		IPackageBinding pkg = elementPackage(tb);
		return pkg == null ? "" : name(pkg);
	}

	public static String packageHeader(String packageName) {
		return packageName == null || packageName.length() == 0 ? "fwd.h"
				: packageName + ".fwd.h";
	}

	public static String qualifiedName(ITypeBinding tb) {
		IPackageBinding pkg = elementPackage(tb);
		return pkg == null ? name(tb) : (name(pkg) + "." + name(tb));
	}

	public static String include(ITypeBinding tb) {
		return include(headerName(tb));
	}

	public static String include(String s) {
		return "#include \"" + s + "\"";
	}

	public static String headerName(ITypeBinding tb) {
		if (isPrimitiveArray(tb)) {
			return "Array.h";
		}

		return qualifiedName(tb) + ".h";
	}

	public static String implName(ITypeBinding tb, String suffix) {
		return qualifiedName(tb) + suffix + ".cpp";
	}

	public static String mainName(ITypeBinding tb) {
		return qualifiedName(tb) + "-main.cpp";
	}

	public static Object constantValue(VariableDeclarationFragment node) {
		Expression expr = node.getInitializer();
		if (expr == null) {
			return null;
		}

		if (!expr.resolveTypeBinding().isPrimitive()) {
			return null;
		}

		if (expr.resolveConstantExpressionValue() == null) {
			return null;
		}

		IVariableBinding vb = node.resolveBinding();
		if (!vb.getType().isPrimitive()) {
			return null;
		}

		if (!isFinal(vb)) {
			return null;
		}

		return checkConstant(expr.resolveConstantExpressionValue());
	}

	public static Object constantValue(IVariableBinding vb) {
		if (!vb.getType().isPrimitive()) {
			return null;
		}

		if (vb.getConstantValue() == null) {
			return null;
		}

		if (!isFinal(vb)) {
			return null;
		}

		return checkConstant(vb.getConstantValue());
	}

	/**
	 * We make static methods out of static non-constexpr variables to get a
	 * chance to initialize the class before variable access
	 */
	public static boolean asMethod(IVariableBinding vb) {
		return vb.isField() && isStatic(vb) && constantValue(vb) == null
				&& !vb.isEnumConstant();
	}

	public static Object checkConstant(Object cv) {
		if (cv instanceof Byte) {
			return "int8_t(" + cv + ")";
		} else if (cv instanceof Character) {
			char ch = (char) cv;

			if (ch == '\'') {
				return "u'\\''";
			}

			if (ch == '\\') {
				return "u'\\\\'";
			}

			if (ch >= 0xd800 && ch <= 0xdfff || ch == 0xffff) {
				// These are not valid for the \\u syntax
				// 0xffff is a G++ bug:
				// http://gcc.gnu.org/bugzilla/show_bug.cgi?id=41698
				return String.format("char16_t(0x%04x)", (int) ch);
			}

			if (ch < ' ' || ch > 127) {
				// These depend on source file charset, so play it safe
				return String.format("u'\\u%04x'", (int) ch);
			}

			return "u'" + ch + "'";
		} else if (cv instanceof Integer) {
			if ((int) cv == Integer.MIN_VALUE) {
				// In C++, the part after '-' is parsed first which overflows
				// so we do a trick
				return "int32_t(-0x7fffffff-1)";
			}

			return "int32_t(" + cv + ")";
		} else if (cv instanceof Long) {
			if ((long) cv == Long.MIN_VALUE) {
				// In C++, the part before '-' is parsed first which overflows
				// so we do a trick
				return "int64_t(-0x7fffffffffffffffLL-1)";
			}

			return "int64_t(" + cv + "ll)";
		} else if (cv instanceof Float) {
			float f = (Float) cv;
			if (Float.isNaN(f)) {
				return "std::numeric_limits<float>::quiet_NaN()";
			} else if (f == Float.POSITIVE_INFINITY) {
				return "std::numeric_limits<float>::infinity()";
			} else if (f == Float.NEGATIVE_INFINITY) {
				return "(-std::numeric_limits<float>::infinity())";
			}

			return cv + "f";
		} else if (cv instanceof Double) {
			double f = (Double) cv;
			if (Double.isNaN(f)) {
				return "std::numeric_limits<double>::quiet_NaN()";
			} else if (f == Double.POSITIVE_INFINITY) {
				return "std::numeric_limits<double>::infinity()";
			} else if (f == Double.NEGATIVE_INFINITY) {
				return "(-std::numeric_limits<double>::infinity())";
			}

			return cv;
		} else if (cv instanceof Short) {
			return "int16_t(" + cv + ")";
		}

		return cv;
	}

	/**
	 * Clean up java-escaped string literals for C++.
	 * 
	 * In java, it is valid to have lone UTF-16 surrogates - in C++, not.
	 */
	public static String stringLiteral(String escaped) {
		Pattern p = Pattern.compile("\\\\u([dD][89abcdefABCDEF]..)");
		Matcher m = p.matcher(escaped);
		return m.replaceAll("\\\\x$1");
	}

	public static String fieldModifiers(ITypeBinding type, int modifiers,
			boolean header, boolean isConstExpr) {
		String ret = "";
		if (header
				&& (Modifier.isStatic(modifiers) || type.isInterface() || isConstExpr)) {
			ret += "static ";
		}

		if (isConstExpr && (Modifier.isFinal(modifiers) || type.isInterface())) {
			ret += "constexpr ";
		}

		return ret;
	}

	public static String methodModifiers(IMethodBinding mb) {
		int modifiers = mb.getModifiers();
		int typeModifiers = mb.getDeclaringClass() == null ? 0 : mb
				.getDeclaringClass().getModifiers();
		if (Modifier.isStatic(modifiers)) {
			return "static ";
		}

		if (Modifier.isFinal(modifiers | typeModifiers)
				|| Modifier.isPrivate(modifiers)) {
			return "";
		}

		return "virtual ";
	}

	public static String variableModifiers(ITypeBinding type, int modifiers) {
		return fieldModifiers(type, modifiers, false, false);
	}

	public static String typeArguments(Collection<Type> parameters) {
		if (parameters.isEmpty()) {
			return "";
		}

		return "/* <" + toCSV(parameters) + "> */";
	}

	public static String typeParameters(Collection<TypeParameter> parameters) {
		if (parameters.isEmpty()) {
			return "";
		}

		return "/* <" + toCSV(parameters) + "> */";
	}

	public static String throwsDecl(Collection<Name> parameters) {
		if (parameters.isEmpty()) {
			return "";
		}

		return " /* throws(" + toCSV(parameters) + ") */";
	}

	public static String annotations(Collection<Annotation> annotations) {
		if (annotations.isEmpty()) {
			return "";
		}

		return "/* " + toCSV(annotations, " ") + " */";
	}

	public static String toCSV(Collection<?> c) {
		return toCSV(c, ", ");
	}

	public static String toCSV(Collection<?> c, String separator) {
		StringBuilder builder = new StringBuilder();
		String s = "";
		for (Object o : c) {
			builder.append(s);
			s = separator;
			builder.append(o);
		}

		return builder.toString();
	}

	public static String primitive(String token) {
		return primitive(PrimitiveType.toCode(token));
	}

	public static String primitive(PrimitiveType.Code code) {
		if (code == PrimitiveType.BOOLEAN) {
			return "bool";
		} else if (code == PrimitiveType.BYTE) {
			return "int8_t";
		} else if (code == PrimitiveType.CHAR) {
			return "char16_t";
		} else if (code == PrimitiveType.DOUBLE) {
			return "double";
		} else if (code == PrimitiveType.FLOAT) {
			return "float";
		} else if (code == PrimitiveType.INT) {
			return "int32_t";
		} else if (code == PrimitiveType.LONG) {
			return "int64_t";
		} else if (code == PrimitiveType.SHORT) {
			return "int16_t";
		} else if (code == PrimitiveType.VOID) {
			return "void";
		} else {
			throw new RuntimeException("Unknown primitive type");
		}
	}

	public static void addDep(ITypeBinding dep, Collection<ITypeBinding> deps) {
		if (dep == null) {
			return;
		}

		if (dep.isNullType()) {
			return;
		}

		dep = dep.getErasure();

		// This ensures that the new dep is always added last (useful when
		// ordering bases)
		for (ITypeBinding d : deps) {
			if (d.isEqualTo(dep)) {
				deps.remove(d);
				break;
			}
		}

		deps.add(dep);
	}

	public static String ref(ITypeBinding tb) {
		return tb.isPrimitive() ? "" : "*";
	}

	public static String refName(IVariableBinding vb) {
		return ref(vb.getType()) + name(vb);
	}

	public static String constVar(IVariableBinding vb) {
		if (!vb.isField() && Modifier.isFinal(vb.getModifiers())) {
			return "const ";
		}

		return "";
	}

	public static String ref(Type t) {
		return t.isPrimitiveType() ? "" : "*";
	}

	public static boolean isDefault(int modifiers) {
		return !Modifier.isPrivate(modifiers)
				&& !Modifier.isProtected(modifiers)
				&& !Modifier.isPublic(modifiers);
	}

	public static boolean isFinal(IVariableBinding vb) {
		return Modifier.isFinal(vb.getModifiers())
				|| (vb.isField() && vb.getDeclaringClass().isInterface());
	}

	public static boolean isStatic(ITypeBinding tb) {
		return Modifier.isStatic(tb.getModifiers());
	}

	public static boolean isStatic(IMethodBinding mb) {
		return Modifier.isStatic(mb.getModifiers());
	}

	public static boolean isStatic(IVariableBinding vb) {
		return Modifier.isStatic(vb.getModifiers())
				|| (vb.isField() && vb.getDeclaringClass() != null && vb
						.getDeclaringClass().isInterface());
	}

	public static boolean hasOuterThis(ITypeBinding tb) {
		if (tb.isLocal()) {
			IMethodBinding mb = tb.getDeclaringMethod();
			if (mb == null) {
				IJavaElement je = tb.getJavaElement();

				try {
					while (je != null) {
						if (je instanceof IInitializer) {
							return !Flags.isStatic(((IInitializer) je)
									.getFlags());
						}

						if (je instanceof IField) {
							IField field = (IField) je;
							return !Flags.isStatic(field.getFlags())
									&& !Flags.isEnum(field.getFlags());
						}

						je = je.getParent();
					}
				} catch (JavaModelException e) {
					throw new Error(e);
				}

				return false;
			}

			return !isStatic(mb);
		}

		if (tb.isNested()) {
			return !isStatic(tb);
		}

		return false;
	}

	public static String outerThis(ITypeBinding tb) {
		return TransformUtil.relativeCName(tb.getDeclaringClass(), tb, false)
				+ " *" + outerThisName(tb);
	}

	public static String outerThisName(ITypeBinding tb) {
		return thisName(tb.getDeclaringClass());
	}

	public static String thisName(ITypeBinding tb) {
		return TransformUtil.name(tb) + "_this";
	}

	/** Filter out C++ keywords */
	public static String keywords(String name) {
		if (keywords.contains(name)) {
			return "_" + name;
		}

		return name;
	}

	public static Set<IMethodBinding> allMethods(ITypeBinding tb, String name,
			ITypeBinding object) {
		Set<IMethodBinding> ret = new TreeSet<IMethodBinding>(
				new BindingComparator());

		methods(tb, name, ret);

		ret.addAll(baseMethods(tb, name, object));

		return ret;
	}

	public static List<IMethodBinding> baseMethods(ITypeBinding tb,
			String name, ITypeBinding object) {
		List<IMethodBinding> ret = new ArrayList<IMethodBinding>();

		// Order significant in case an interface is inherited multiple times
		Collection<ITypeBinding> bases = TypeUtil.allBases(tb, object);
		for (ITypeBinding base : bases) {
			methods(base, name, ret);
		}

		return ret;
	}

	private static void methods(ITypeBinding tb, String name,
			Collection<IMethodBinding> ret) {
		for (IMethodBinding mb : tb.getDeclaredMethods()) {
			if (mb.getName().equals(name)) {
				ret.add(mb);
			}
		}
	}

	public static String indent(int n) {
		String ret = "";
		for (int i = 0; i < n; i++)
			ret += "    ";
		return ret;
	}

	public static String virtual(ITypeBinding tb) {
		if (tb.isInterface() || same(tb, Object.class)) {
			return "virtual ";
		}

		return "";
	}

	public static PrintWriter openImpl(IPath root, ITypeBinding tb,
			String suffix) throws IOException {

		FileOutputStream fos = new FileOutputStream(root.append(
				TransformUtil.implName(tb, suffix)).toFile());

		PrintWriter pw = new PrintWriter(fos);

		if (tb.getJavaElement() != null) {
			pw.println("// Generated from " + tb.getJavaElement().getPath());
		} else {
			pw.println("// Generated");
		}

		pw.println(include(tb));
		pw.println();

		return pw;
	}

	public static void print(PrintWriter out, Object... objects) {
		for (Object o : objects) {
			out.print(o);
		}
	}

	public static void println(PrintWriter out, Object... objects) {
		print(out, objects);
		out.println();
	}

	public static void printi(PrintWriter out, int indent, Object... objects) {
		out.print(indent(indent));
		print(out, objects);
	}

	public static void printlni(PrintWriter out, int indent, Object... objects) {
		printi(out, indent, objects);
		out.println();
	}

	public static void printParams(PrintWriter out, ITypeBinding tb,
			IMethodBinding mb, boolean parens, Collection<ITypeBinding> deps) {
		if (parens) {
			out.print("(");
		}

		for (int i = 0; i < mb.getParameterTypes().length; ++i) {
			if (i > 0)
				out.print(", ");

			ITypeBinding pb = mb.getParameterTypes()[i];
			addDep(pb, deps);

			out.print(relativeCName(pb, tb, true));
			out.print(" ");
			out.print(ref(pb));

			out.print(paramName(mb, i));
		}

		if (parens) {
			out.print(")");
		}
	}

	public static String paramName(IMethodBinding mb, int i) {
		try {
			IMethod md = (IMethod) mb.getJavaElement();
			return md == null ? "a" + i : md.getParameterNames()[i] + "_";
		} catch (JavaModelException e) {
			return "a" + i;
		}
	}

	public static IMethodBinding getSuperMethod(IMethodBinding mb,
			ITypeBinding tb) {
		for (IMethodBinding mb2 : tb.getDeclaredMethods()) {
			if (mb.overrides(mb2)) {
				return mb2;
			}
		}

		if (tb.getSuperclass() != null) {
			IMethodBinding mb2 = getSuperMethod(mb, tb.getSuperclass());
			if (mb2 != null) {
				return mb2;
			}
		}

		for (ITypeBinding ib : tb.getInterfaces()) {
			IMethodBinding mb2 = getSuperMethod(mb, ib);
			if (mb2 != null) {
				return mb2;
			}
		}

		return null;
	}

	/** Check if super-interface (or Object) has the same method already */
	public static boolean baseHasSame(IMethodBinding mb, ITypeBinding tb,
			Transformer ctx) {
		for (ITypeBinding ib : tb.getInterfaces()) {
			if (getSuperMethod(mb, ib) != null) {
				return true;
			}

			if (baseHasSame(mb, ib, ctx)) {
				return true;
			}
		}

		if (tb.isInterface()) {
			ITypeBinding ob = ctx.resolve(Object.class);
			for (IMethodBinding mb2 : ob.getDeclaredMethods()) {
				if (sameSignature(mb, mb2)) {
					return true;
				}
			}
		}

		return false;
	}

	public static IMethodBinding getSuperMethod(IMethodBinding mb) {
		if (isStatic(mb) || mb.isConstructor()) {
			return null;
		}

		if (mb.getDeclaringClass().getSuperclass() != null) {
			IMethodBinding mb2 = getSuperMethod(mb, mb.getDeclaringClass()
					.getSuperclass());
			if (mb2 != null) {
				return mb2;
			}
		}

		for (ITypeBinding tb : mb.getDeclaringClass().getInterfaces()) {
			IMethodBinding mb2 = getSuperMethod(mb, tb);
			if (mb2 != null) {
				return mb2;
			}
		}

		return null;
	}

	public static String declareBridge(PrintWriter pw, ITypeBinding tb,
			IMethodBinding mb, Collection<ITypeBinding> softDeps, String access) {
		if (!mb.isConstructor()) {
			IMethodBinding mb2 = getSuperMethod(mb);
			if (needsBridge(mb, mb2)) {
				mb2 = mb2.getMethodDeclaration();

				access = Header.printAccess(pw, mb2, access);
				pw.print(TransformUtil.indent(1));

				TransformUtil.printSignature(pw, tb, mb2, softDeps, false);

				pw.println(";");
			}
		}

		return access;
	}

	public static List<ITypeBinding> defineBridge(PrintWriter pw,
			ITypeBinding tb, IMethodBinding mb,
			Collection<ITypeBinding> softDeps) {
		List<ITypeBinding> deps = new ArrayList<ITypeBinding>();
		if (!mb.isConstructor()) {
			IMethodBinding mb2 = getSuperMethod(mb);
			if (needsBridge(mb, mb2)) {
				mb2 = mb2.getMethodDeclaration();

				printSignature(pw, tb, mb2, softDeps, true);

				pw.println();
				pw.println("{ ");
				pw.print(indent(1));
				if (!isVoid(mb.getReturnType())) {
					pw.print("return ");
				}

				pw.print(name(mb2));
				pw.print("(");
				for (int i = 0; i < mb2.getParameterTypes().length; ++i) {
					if (i > 0)
						pw.print(", ");
					ITypeBinding pb = mb.getParameterTypes()[i];
					ITypeBinding pb2 = mb2.getParameterTypes()[i];

					if (!pb.isEqualTo(pb2)) {
						deps.add(pb);
						pw.print("dynamic_cast< ");
						pw.print(relativeCName(pb, tb, false));
						pw.print(ref(pb));
						pw.print(" >(");
						pw.print(TransformUtil.paramName(mb2, i));
						pw.print(")");
					} else {
						pw.print(TransformUtil.paramName(mb2, i));
					}
				}

				pw.println(");");
				pw.println("}");
				pw.println("");
			}
		}

		return deps;
	}

	private static boolean needsBridge(IMethodBinding mb, IMethodBinding mb2) {
		return mb2 != null && !sameSignature(mb, mb2.getMethodDeclaration())
				&& !returnCovariant(mb, mb2.getMethodDeclaration());
	}

	private static boolean sameSignature(IMethodBinding mb, IMethodBinding mb2) {
		if (!mb.getName().equals(mb2.getName())) {
			return false;
		}

		return sameReturn(mb, mb2) && sameParameters(mb, mb2);
	}

	private static boolean sameReturn(IMethodBinding mb, IMethodBinding mb2) {
		return mb
				.getMethodDeclaration()
				.getReturnType()
				.getErasure()
				.isEqualTo(
						mb2.getMethodDeclaration().getReturnType().getErasure());

	}

	public static boolean sameParameters(IMethodBinding mb, IMethodBinding mb2) {
		if (mb.getParameterTypes().length != mb2.getParameterTypes().length) {
			return false;
		}

		mb = mb.getMethodDeclaration();
		mb2 = mb2.getMethodDeclaration();

		for (int i = 0; i < mb.getParameterTypes().length; ++i) {
			if (!mb.getParameterTypes()[i].getErasure().isEqualTo(
					mb2.getParameterTypes()[i].getErasure())) {
				return false;
			}
		}

		return true;
	}

	public static ITypeBinding returnType(MethodDeclaration md) {
		return md.resolveBinding().getReturnType();
	}

	public static boolean returnCovariant(IMethodBinding mb, IMethodBinding mb2) {
		return !sameReturn(mb, mb2) && sameParameters(mb, mb2);
	}

	public static void printSignature(PrintWriter pw, ITypeBinding tb,
			IMethodBinding mb, Collection<ITypeBinding> softDeps,
			boolean qualified) {
		if (mb.isConstructor()) {
			pw.print("void " + CTOR);
		} else {
			ITypeBinding rt = mb.getReturnType();
			addDep(rt, softDeps);

			if (!qualified) {
				pw.print(methodModifiers(mb));
				pw.print(relativeCName(rt, tb, true));
			} else {
				pw.print(qualifiedCName(rt, true));
			}

			pw.print(" ");
			pw.print(ref(rt));

			if (qualified) {
				pw.print(qualifiedCName(tb, true));
				pw.print("::");
			}

			pw.print(name(mb));
		}

		printParams(pw, tb, mb, true, softDeps);
	}

	public static String printNestedParams(PrintWriter pw, ITypeBinding type,
			Collection<IVariableBinding> closures) {
		String sep = "";
		if (TransformUtil.hasOuterThis(type)) {
			pw.print(TransformUtil.outerThis(type));
			sep = ", ";
		}

		if (closures != null) {
			for (IVariableBinding closure : closures) {
				pw.print(sep
						+ TransformUtil.relativeCName(closure.getType(), type,
								true) + " " + TransformUtil.refName(closure));
				sep = ", ";
			}
		}

		return sep;
	}

	public static boolean isVoid(ITypeBinding tb) {
		return tb.getName().equals("void");
	}

	public static boolean isMain(IMethodBinding mb) {
		return mb.getReturnType() != null
				&& isVoid(mb.getReturnType())
				&& mb.getName().equals("main")
				&& mb.getParameterTypes().length == 1
				&& mb.getParameterTypes()[0].isArray()
				&& same(mb.getParameterTypes()[0].getComponentType(),
						String.class);
	}

	public static void printStringSupport(ITypeBinding tb, PrintWriter pw) {
		if (!same(tb, String.class)) {
			return;
		}

		pw.println();
		pw.println("java::lang::String *join(java::lang::String *lhs, java::lang::String *rhs);");
		for (String type : new String[] { "java::lang::Object *", "bool ",
				"int8_t ", "char16_t ", "double ", "float ", "int32_t ",
				"int64_t ", "int16_t " }) {
			pw.println("java::lang::String *join(java::lang::String *lhs, "
					+ type + "rhs);");
			pw.println("java::lang::String *join(" + type
					+ "lhs, java::lang::String *rhs);");
		}

		pw.println("namespace java { namespace lang { String *operator \"\" _j(const char16_t *p, size_t n); } }");
		pw.println("using java::lang::operator \"\" _j;");
		pw.println();
	}

	public static void printClassLiteral(PrintWriter out, ITypeBinding type) {
		out.println("extern ::java::lang::Class *class_(const char16_t *c, int n);");
		out.println();
		if (type.isArray() && type.getComponentType().isPrimitive()) {
			out.println("template<>");
		}
		out.println("::java::lang::Class *" + qualifiedCName(type, false)
				+ "::class_()");
		out.println("{");
		out.println("    static ::java::lang::Class *c = ::class_(u\""
				+ type.getQualifiedName() + "\", "
				+ type.getQualifiedName().length() + ");");
		out.println("    return c;");

		out.println("}");
		out.println();
	}

	public static void printGetClass(PrintWriter pw, ITypeBinding type) {
		if (type.isArray() && type.getComponentType().isPrimitive()) {
			pw.println("template<>");
		}
		pw.println("::java::lang::Class *" + qualifiedCName(type, true) + "::"
				+ GET_CLASS + "()");
		pw.println("{");
		pw.println(indent(1) + "return class_();");
		pw.println("}");
		pw.println();
	}

	public static void writeTemplate(InputStream template, File target,
			Object... params) throws IOException {
		String format = new Scanner(template).useDelimiter("\\A").next();

		try (PrintWriter pw = new PrintWriter(new FileOutputStream(target))) {
			pw.format(format, params);
		}
	}

	public static boolean isPrimitiveArray(ITypeBinding tb) {
		return tb.isArray() && tb.getComponentType().isPrimitive();
	}

	public static boolean same(ITypeBinding type, Class<?> klazz) {
		return type.getErasure().getQualifiedName().equals(klazz.getName());
	}
}
