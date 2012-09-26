package se.arnetheduck.j2c.transform;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Modifier;
import java.text.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
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
	public static final String NATIVE = "native";
	public static final String STUB = "stub";

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

	public static String packageHeader(String packageName) {
		return packageName == null || packageName.isEmpty() ? "fwd.hpp"
				: toFileName(packageName) + "/fwd.hpp";
	}

	public static String qualifiedName(ITypeBinding tb) {
		IPackageBinding pkg = elementPackage(tb);
		return pkg == null || pkg.getName().isEmpty() ? CName.of(tb) : (CName
				.of(pkg) + "." + CName.of(tb));
	}

	public static IPackageBinding elementPackage(ITypeBinding tb) {
		// When processing generics, only the erasure will have a package
		return tb.isArray() ? tb.getElementType().getErasure().getPackage()
				: tb.getErasure().getPackage();
	}

	public static String include(ITypeBinding tb) {
		return include(headerName(tb));
	}

	public static String include(String s) {
		return "#include <" + s + ">";
	}

	public static IPath headerPath(IPath root, ITypeBinding tb) {
		return headerPath(root, headerName(tb));
	}

	public static IPath headerPath(IPath root, String name) {
		return root.append("include").append(name);
	}

	public static String headerName(ITypeBinding tb) {
		if (isPrimitiveArray(tb)) {
			return "Array.hpp";
		}

		return toFileName(qualifiedName(tb)) + ".hpp";
	}

	public static IPath implPath(IPath root, ITypeBinding tb, String suffix) {
		return root.append(suffix.length() == 0 ? "src" : suffix).append(
				implName(tb, suffix));
	}

	public static String implName(ITypeBinding tb, String suffix) {
		if (suffix.length() > 0) {
			suffix = "-" + suffix;
		}

		return toFileName(qualifiedName(tb)) + suffix + ".cpp";
	}

	public static String mainName(ITypeBinding tb) {
		return toFileName(qualifiedName(tb)) + "-main.cpp";
	}

	private static String toFileName(String s) {
		// Annoying character to escape
		return s.replaceAll("\\$", "_").replaceAll("\\.", "/");
	}

	public static Object constantValue(VariableDeclarationFragment node) {
		Expression expr = node.getInitializer();
		if (expr == null) {
			return null;
		}

		Object v = expr.resolveConstantExpressionValue();
		if (v == null) {
			return null;
		}

		IVariableBinding vb = node.resolveBinding();
		if (!vb.getType().isPrimitive() && !(v instanceof String)) {
			return null;
		}

		if (!isFinal(vb)) {
			return null;
		}

		return v;
	}

	public static Object constantValue(IVariableBinding vb) {
		Object v = vb.getConstantValue();
		if (v == null) {
			return null;
		}

		if (!vb.getType().isPrimitive() && !(v instanceof String)) {
			return null;
		}

		if (!isFinal(vb)) {
			return null;
		}

		return v;
	}

	/**
	 * We make static methods out of static non-constexpr variables to get a
	 * chance to initialize the class before variable access
	 */
	public static boolean asMethod(IVariableBinding vb) {
		return vb.isField()
				&& isStatic(vb)
				&& (constantValue(vb) == null || constantValue(vb) instanceof String)
				&& !vb.isEnumConstant();
	}

	public static String checkConstant(Object cv) {
		if (cv instanceof Byte) {
			return "int8_t(" + cv + ")";
		}

		if (cv instanceof Character) {
			char ch = (char) cv;

			if (ch == '\'') {
				return "u'\\''";
			}

			if (ch == '\\') {
				return "u'\\\\'";
			}

			if (ch >= 0xd800 && ch <= 0xdfff || ch == 0x0000 || ch == 0xffff) {
				// These are not valid for the \\u syntax
				// 0x0000 is a G++ bug:
				// http://gcc.gnu.org/bugzilla/show_bug.cgi?id=53690
				// 0xffff is a G++ bug:
				// http://gcc.gnu.org/bugzilla/show_bug.cgi?id=41698
				return String.format("char16_t(0x%04x)", (int) ch);
			}

			if (ch < ' ' || ch > 127) {
				// These depend on source file charset, so play it safe
				return String.format("u'\\u%04x'", (int) ch);
			}

			return "u'" + ch + "'";
		}

		if (cv instanceof Integer) {
			if ((int) cv == Integer.MIN_VALUE) {
				// In C++, the part after '-' is parsed first which overflows
				// so we do a trick
				return "int32_t(-0x7fffffff-1)";
			}

			return "int32_t(" + cv + ")";
		}

		if (cv instanceof Long) {
			if ((long) cv == Long.MIN_VALUE) {
				// In C++, the part before '-' is parsed first which overflows
				// so we do a trick
				return "int64_t(-0x7fffffffffffffffLL-1)";
			}

			return "int64_t(" + cv + "ll)";
		}

		if (cv instanceof Float) {
			float f = (Float) cv;
			if (Float.isNaN(f)) {
				return "std::numeric_limits<float>::quiet_NaN()";
			} else if (f == Float.POSITIVE_INFINITY) {
				return "std::numeric_limits<float>::infinity()";
			} else if (f == Float.NEGATIVE_INFINITY) {
				return "(-std::numeric_limits<float>::infinity())";
			}

			return cv + "f";
		}

		if (cv instanceof Double) {
			double f = (Double) cv;
			if (Double.isNaN(f)) {
				return "std::numeric_limits<double>::quiet_NaN()";
			} else if (f == Double.POSITIVE_INFINITY) {
				return "std::numeric_limits<double>::infinity()";
			} else if (f == Double.NEGATIVE_INFINITY) {
				return "(-std::numeric_limits<double>::infinity())";
			}

			return "" + cv;
		}

		if (cv instanceof Short) {
			return "int16_t(" + cv + ")";
		}

		return cv == null ? null : cv.toString();
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

	public static boolean addDep(ITypeBinding dep, Collection<ITypeBinding> deps) {
		if (dep == null) {
			return false;
		}

		if (dep.isNullType() || isVoid(dep)) {
			return false;
		}

		dep = dep.getErasure();

		boolean found = false;
		// This ensures that the new dep is always added last (useful when
		// ordering bases)
		for (ITypeBinding d : deps) {
			if (d.isEqualTo(dep)) {
				deps.remove(d);
				found = true;
				break;
			}
		}

		deps.add(dep);
		return !found;
	}

	public static String ref(ITypeBinding tb) {
		return tb.isPrimitive() ? "" : "*";
	}

	public static String refName(IVariableBinding vb) {
		return ref(vb.getType()) + CName.of(vb);
	}

	public static String constVar(IVariableBinding vb) {
		if (isConstVar(vb)) {
			return "const ";
		}

		return "";
	}

	public static boolean isConstVar(IVariableBinding vb) {
		return !vb.isField() && Modifier.isFinal(vb.getModifiers());
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
		if (tb.isArray() || tb.isInterface() || tb.isAnnotation()) {
			return false;
		}

		if (tb.isLocal()) {
			IMethodBinding mb = tb.getDeclaringMethod();
			if (mb == null) {
				// Could be https://bugs.eclipse.org/bugs/show_bug.cgi?id=383486
				IJavaElement je = tb.getJavaElement();

				try {
					while (je != null) {
						if (je instanceof IMethod) {
							return !Flags.isStatic(((IMethod) je).getFlags());
						}

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
		return CName.relative(tb.getDeclaringClass(), tb, false) + " *"
				+ outerThisName(tb);
	}

	public static String outerThisName(ITypeBinding tb) {
		return thisName(tb.getDeclaringClass());
	}

	public static String thisName(ITypeBinding tb) {
		return CName.of(tb) + "_this";
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

	public static void printi(PrintWriter out, int indent, String string) {
		out.print(indent(indent));
		out.print(string);
	}

	public static void printlni(PrintWriter out, int indent, String string) {
		printi(out, indent, string);
		out.println();
	}

	public static void printParams(PrintWriter out, ITypeBinding tb,
			IMethodBinding mb, boolean parens, DepInfo deps) {
		if (parens) {
			out.print("(");
		}

		for (int i = 0; i < mb.getParameterTypes().length; ++i) {
			if (i > 0)
				out.print(", ");

			ITypeBinding pb = mb.getParameterTypes()[i];
			deps.soft(pb);

			out.print(CName.relative(pb, tb, true));
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
			return md == null ? "a" + i : CName
					.keywords(md.getParameterNames()[i]);
		} catch (JavaModelException e) {
			return "a" + i;
		}
	}

	/** Check if super-interface (or Object) has the same method already */
	public static boolean baseHasSame(IMethodBinding mb, ITypeBinding tb,
			ITypeBinding object) {
		for (ITypeBinding ib : tb.getInterfaces()) {
			for (IMethodBinding mb2 : ib.getDeclaredMethods()) {
				if (sameSignature(mb, mb2)) {
					return true;
				}
			}

			if (baseHasSame(mb, ib, object)) {
				return true;
			}
		}

		if (tb.isInterface()) {
			for (IMethodBinding mb2 : object.getDeclaredMethods()) {
				if (sameSignature(mb, mb2)) {
					return true;
				}
			}
		}

		return false;
	}

	public static boolean asBaseConstructor(IMethodBinding mb, ITypeBinding tb) {
		if (!mb.isConstructor()) {
			return false;
		}

		if (!Modifier.isPrivate(mb.getModifiers())) {
			return true;
		}

		IJavaElement mbje = mb.getJavaElement();
		IJavaElement tbje = tb.getJavaElement();
		if (mbje == null || tbje == null) {
			return false;
		}

		IJavaElement mbcu = mbje.getAncestor(IJavaElement.COMPILATION_UNIT);
		IJavaElement tbcu = tbje.getAncestor(IJavaElement.COMPILATION_UNIT);
		return mbcu != null && mbcu.equals(tbcu);
	}

	public static Collection<ITypeBinding> returnDeps(ITypeBinding type,
			IMethodBinding mb, ITypeBinding object) {
		List<ITypeBinding> ret = new ArrayList<ITypeBinding>();
		if (mb.isConstructor()) {
			return ret;
		}

		List<IMethodBinding> methods = TypeUtil.methods(
				TypeUtil.allBases(type, object), TypeUtil.overrides(mb));

		for (IMethodBinding mb2 : methods) {
			if (!mb2.isConstructor() && returnCovariant(mb, mb2)) {
				addDep(mb.getReturnType(), ret);
			}
		}

		return ret;
	}

	public static String declareBridge(PrintWriter pw, ITypeBinding tb,
			IMethodBinding mb, DepInfo deps, String access) {
		List<IMethodBinding> methods = TypeUtil.methods(
				TypeUtil.allBases(tb, null), TypeUtil.overrides(mb));
		for (IMethodBinding mb2 : methods) {
			if (needsBridge(mb, mb2)) {
				mb2 = mb2.getMethodDeclaration();

				access = Header.printAccess(pw, mb2, access);
				pw.print(indent(1));

				printSignature(pw, tb, mb2, deps, false);

				pw.println(";");
				break;
			}
		}

		return access;
	}

	public static void defineBridge(PrintWriter pw, ITypeBinding tb,
			IMethodBinding mb, DepInfo deps) {
		List<IMethodBinding> methods = TypeUtil.methods(
				TypeUtil.allBases(tb, null), TypeUtil.overrides(mb));
		for (IMethodBinding mb2 : methods) {
			if (needsBridge(mb, mb2)) {
				mb2 = mb2.getMethodDeclaration();

				printSignature(pw, tb, mb2, deps, true);

				pw.println();
				pw.println("{ ");
				pw.print(indent(1));
				if (!isVoid(mb.getReturnType())) {
					pw.print("return ");

					if (!mb2.getReturnType().getErasure()
							.isEqualTo(mb.getReturnType().getErasure())) {
						deps.hard(mb.getReturnType());
					}
				}

				pw.print(CName.of(mb2));
				pw.print("(");
				for (int i = 0; i < mb2.getParameterTypes().length; ++i) {
					if (i > 0)
						pw.print(", ");
					ITypeBinding pb = mb.getParameterTypes()[i];
					ITypeBinding pb2 = mb2.getParameterTypes()[i];

					if (!pb.isEqualTo(pb2)) {
						deps.hard(pb);
						pw.print("dynamic_cast< ");
						pw.print(CName.relative(pb, tb, false));
						pw.print(ref(pb));
						pw.print(" >(");
						pw.print(paramName(mb2, i));
						pw.print(")");
					} else {
						pw.print(paramName(mb2, i));
					}
				}

				pw.println(");");
				pw.println("}");
				pw.println("");
				break;
			}
		}
	}

	private static boolean needsBridge(IMethodBinding mb, IMethodBinding mb2) {
		return !mb.isConstructor() && mb2 != null
				&& !sameSignature(mb, mb2.getMethodDeclaration())
				&& !returnCovariant(mb, mb2.getMethodDeclaration());
	}

	// There's a problem with IMethodBinding.isSubsignature, so we do it by hand
	// https://bugs.eclipse.org/bugs/show_bug.cgi?id=382907
	public static boolean isSubsignature(IMethodBinding a, IMethodBinding b) {
		return !a.isConstructor() && a.getName().equals(b.getName())
				&& (sameParameters(a, b, false) || sameParameters(a, b, true));
	}

	private static boolean sameSignature(IMethodBinding mb, IMethodBinding mb2) {
		if (!mb.getName().equals(mb2.getName())) {
			return false;
		}

		return sameReturn(mb, mb2) && sameParameters(mb, mb2, true);
	}

	private static boolean sameReturn(IMethodBinding mb, IMethodBinding mb2) {
		return mb
				.getMethodDeclaration()
				.getReturnType()
				.getErasure()
				.isEqualTo(
						mb2.getMethodDeclaration().getReturnType().getErasure());

	}

	public static boolean sameParameters(IMethodBinding mb, IMethodBinding mb2,
			boolean erase) {
		if (mb.getParameterTypes().length != mb2.getParameterTypes().length) {
			return false;
		}

		if (erase) {
			mb = mb.getMethodDeclaration();
			mb2 = mb2.getMethodDeclaration();
		}

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
		return !sameReturn(mb, mb2) && sameParameters(mb, mb2, true);
	}

	public static void printSignature(PrintWriter pw, ITypeBinding tb,
			IMethodBinding mb, DepInfo deps, boolean qualified) {
		printSignature(pw, tb, mb, mb.getReturnType(), deps, qualified);
	}

	public static void printSignature(PrintWriter pw, ITypeBinding tb,
			IMethodBinding mb, ITypeBinding rt, DepInfo deps, boolean qualified) {
		if (mb.isConstructor()) {
			pw.print("void ");
			if (qualified) {
				pw.print(CName.qualified(tb, true));
				pw.print("::");
			}

			pw.print(CName.CTOR);
		} else {
			deps.soft(rt);

			if (!qualified) {
				pw.print(methodModifiers(mb));
				pw.print(CName.relative(rt, tb, true));
			} else {
				pw.print(CName.qualified(rt, true));
			}

			pw.print(" ");
			pw.print(ref(rt));

			if (qualified) {
				pw.print(CName.qualified(tb, true));
				pw.print("::");
			}

			pw.print(CName.of(mb));
		}

		printParams(pw, tb, mb, true, deps);
	}

	public static String printNestedParams(PrintWriter pw, ITypeBinding type,
			Collection<IVariableBinding> closures) {
		String sep = "";
		if (hasOuterThis(type)) {
			pw.print(outerThis(type));
			sep = ", ";
		}

		if (closures != null) {
			for (IVariableBinding closure : closures) {
				pw.print(sep + CName.relative(closure.getType(), type, true)
						+ " " + refName(closure));
				sep = ", ";
			}
		}

		return sep;
	}

	public static boolean isVoid(ITypeBinding tb) {
		return tb == null || tb.getName().equals("void");
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

		pw.println("namespace java { namespace lang { String *operator \"\" _j(const char16_t *p, size_t n); } }");
		pw.println("using java::lang::operator \"\" _j;");
		pw.println();
	}

	public static void writeTemplate(InputStream template, File target,
			Object... params) throws IOException {
		String format = read(template);

		try (PrintWriter pw = new PrintWriter(open(target))) {
			pw.format(format, params);
		}
	}

	public static void writeResource(InputStream resource, File target)
			throws IOException {
		try (PrintWriter pw = new PrintWriter(open(target))) {
			String txt = read(resource);
			pw.write(txt);
		}
	}

	public static String readResource(String resource) {
		try (InputStream is = TransformUtil.class.getResourceAsStream(resource)) {
			return read(is);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static String read(InputStream resource) {
		return new Scanner(resource).useDelimiter("\\A").next();
	}

	public static FileOutputStream open(File target)
			throws FileNotFoundException {
		if (!target.getParentFile().exists()) {
			target.getParentFile().mkdirs();
		}

		return new FileOutputStream(target);
	}

	public static boolean isPrimitiveArray(ITypeBinding tb) {
		return tb.isArray() && tb.getComponentType().isPrimitive();
	}

	public static boolean same(ITypeBinding type, Class<?> klazz) {
		return type.getErasure().getQualifiedName().equals(klazz.getName());
	}

	public static boolean variableErased(IVariableBinding b) {
		return !b.getType().isRawType()
				&& !b.getType().isEqualTo(
						b.getVariableDeclaration().getType().getErasure());
	}

	public static boolean returnErased(IMethodBinding b) {
		return !b.getReturnType().isEqualTo(
				b.getMethodDeclaration().getReturnType().getErasure());
	}
}
