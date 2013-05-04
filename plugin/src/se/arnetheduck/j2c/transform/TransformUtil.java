package se.arnetheduck.j2c.transform;

import java.io.PrintWriter;
import java.lang.reflect.Modifier;
import java.text.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Initializer;
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

	public static String packageHeader(String project, String packageName) {
		String prefix = packageName == null || packageName.isEmpty() ? ""
				: toFileName(packageName) + "/";

		return prefix + "fwd-" + project + ".hpp";
	}

	public static String qualifiedName(ITypeBinding tb) {
		IPackageBinding pkg = elementPackage(tb);
		return pkg == null || pkg.getName().isEmpty() ? CName.of(tb) : (CName
				.of(pkg) + "." + CName.of(tb));
	}

	/** The type of a variable, taking volatile into account */
	public static String varTypeCName(int modifiers, ITypeBinding tb,
			ITypeBinding type, DepInfo deps) {
		boolean vol = Modifier.isVolatile(modifiers);

		String ret = "";
		if (vol) {
			ret += "std::atomic< ";
			deps.setNeedsAtomic();
		}

		ret += relativeRef(tb, type, true);

		if (vol) {
			ret += " >";
		}

		return ret;
	}

	/** The type of a variable, taking volatile into account */
	public static String varTypeCName(int modifiers, ITypeBinding tb,
			DepInfo deps) {
		boolean vol = Modifier.isVolatile(modifiers);

		String ret = "";
		if (vol) {
			ret += "std::atomic< ";
			deps.setNeedsAtomic();
		}

		ret += qualifiedRef(tb, false);

		if (vol) {
			ret += " >";
		}

		return ret;
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
		return root.append("src").append(name);
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

	/** JLS §4.12.5 Initial Values of Variables */
	public static String initialValue(IVariableBinding vb) {
		Object cv = constexprValue(vb);
		if (cv != null) {
			return checkConstant(cv);
		}

		if (isStatic(vb)) {
			// Statics don't need explicit initialization
			return null;
		}

		// The rest match C++'s default initialization values
		return "";
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

	public static Object constexprValue(VariableDeclarationFragment node) {
		Object cv = constantValue(node);
		return cv instanceof String ? null : cv;
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

	public static Object constexprValue(IVariableBinding vb) {
		Object cv = constantValue(vb);
		return cv instanceof String ? null : cv;
	}

	/**
	 * We make static methods out of static non-constexpr variables to get a
	 * chance to initialize the class before variable access
	 */
	public static boolean asMethod(IVariableBinding vb) {
		return vb.isField() && isStatic(vb) && constexprValue(vb) == null
				&& !vb.isEnumConstant();
	}

	public static String checkConstant(Object cv) {
		if (cv instanceof Byte) {
			return "int8_t(" + cv + ")";
		}

		if (cv instanceof Character) {
			char ch = (Character) cv;

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
			if ((Integer) cv == Integer.MIN_VALUE) {
				// In C++, the part after '-' is parsed first which overflows
				// so we do a trick
				return "int32_t(-0x7fffffff-1)";
			}

			return "int32_t(" + cv + ")";
		}

		if (cv instanceof Long) {
			if ((Long) cv == Long.MIN_VALUE) {
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
		if (isStatic(mb)) {
			return "static ";
		}

		if (isPrivate(mb)) {
			return "";
		}

		if (isFinal(mb)) {
			return "";
		}

		if (needsSpecifier(mb)) {
			return "";
		}

		// Java methods virtual by default
		return "virtual ";
	}

	public static String methodSpecifiers(IMethodBinding mb) {
		if (Modifier.isAbstract(mb.getModifiers())) {
			return " = 0";
		}

		if (needsSpecifier(mb)) {
			/*
			 * Can't do this because the method might need a bridge further down
			 * the inheritance chain
			 *
			 * if (isFinal(mb)) { return " final"; }
			 */
			return " override";
		}

		return "";
	}

	/** Does the method need a final/override specifier? */
	public static final boolean needsSpecifier(IMethodBinding mb) {
		List<IMethodBinding> baseMethods = TypeUtil.methods(
				TypeUtil.allBases(mb.getDeclaringClass(), null),
				TypeUtil.overrides(mb));

		for (IMethodBinding mb2 : baseMethods) {
			if (!needsBridge(mb, mb2)) {
				return true;
			}
		}

		return false;
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

	public static String qualifiedRef(ITypeBinding tb, boolean global) {
		return CName.qualified(tb, global) + ref(tb);
	}

	public static String relativeRef(ITypeBinding tb, ITypeBinding root,
			boolean global) {
		return CName.relative(tb, root, global) + ref(tb);
	}

	public static String constVar(IVariableBinding vb) {
		if (isConstVar(vb)) {
			return "const ";
		}

		return "";
	}

	public static boolean isConstVar(IVariableBinding vb) {
		return !vb.isField() && isFinal(vb);
	}

	public static String ref(Type t) {
		return t.isPrimitiveType() ? "" : "*";
	}

	public static boolean isDefault(int modifiers) {
		return !Modifier.isPrivate(modifiers)
				&& !Modifier.isProtected(modifiers)
				&& !Modifier.isPublic(modifiers);
	}

	public static boolean isFinal(ITypeBinding tb) {
		return Modifier.isFinal(tb.getModifiers());
	}

	public static boolean isFinal(IVariableBinding vb) {
		return Modifier.isFinal(vb.getModifiers())
				|| (vb.isField() && vb.getDeclaringClass().isInterface());
	}

	public static boolean isFinal(IMethodBinding mb) {
		return Modifier.isFinal(mb.getModifiers())
				|| (mb.getDeclaringClass() != null && isFinal(mb
						.getDeclaringClass()));
	}

	public static boolean isStatic(ITypeBinding tb) {
		return Modifier.isStatic(tb.getModifiers());
	}

	public static boolean isStatic(IMethodBinding mb) {
		return Modifier.isStatic(mb.getModifiers());
	}

	public static boolean isStatic(FieldDeclaration declaration) {
		return Modifier.isStatic(declaration.getModifiers());
	}

	public static boolean isStatic(VariableDeclarationFragment fragment) {
		return isStatic(fragment.resolveBinding());
	}

	public static boolean isStatic(Initializer initializer) {
		return Modifier.isStatic(initializer.getModifiers());
	}

	public static boolean isPrivate(IMethodBinding mb) {
		return Modifier.isPrivate(mb.getModifiers());
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

	public static String virtual(ITypeBinding type, ITypeBinding base) {
		if (isFinal(type)) {
			if (same(base, Object.class) && type.getInterfaces().length == 0) {
				// No interfaces, so Object only inherited once
				return "";
			}

			if (base.isInterface() && TypeUtil.countBases(type, base) == 1) {
				// Interface only inherited once in the chain
				return "";
			}
		}

		if (base.isInterface() || same(base, Object.class)) {
			// Might be inherited more than once
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

			out.print(TransformUtil.relativeRef(pb, tb, true));
			out.print(" ");

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
			if (!mb2.isConstructor() && returnCovariant(mb, mb2)
					&& !returnType(type, mb).isEqualTo(returnType(type, mb2))) {
				addDep(returnType(type, mb), ret);
			}
		}

		return ret;
	}

	public static String declareBridge(Transformer ctx, PrintWriter pw,
			ITypeBinding tb, IMethodBinding mb, DepInfo deps, String access) {
		List<IMethodBinding> methods = TypeUtil.methods(
				TypeUtil.allBases(tb, null), TypeUtil.overrides(mb));
		for (IMethodBinding mb2 : methods) {
			if (needsBridge(mb, mb2)) {
				mb2 = mb2.getMethodDeclaration();

				access = Header.printAccess(pw, mb2, access);
				pw.print(indent(1));

				printSignature(ctx, pw, tb, mb2, deps, false);

				pw.print(" override");

				pw.println(";");
				break;
			}
		}

		return access;
	}

	public static void defineBridge(Transformer ctx, PrintWriter pw,
			ITypeBinding tb, IMethodBinding mb, DepInfo deps) {
		List<IMethodBinding> methods = TypeUtil.methods(
				TypeUtil.allBases(tb, null), TypeUtil.overrides(mb));
		for (IMethodBinding mb2 : methods) {
			if (needsBridge(mb, mb2)) {
				mb2 = mb2.getMethodDeclaration();

				printSignature(ctx, pw, tb, mb2, deps, true);

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
						pw.print(TransformUtil.relativeRef(pb, tb, true));
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

	public static ITypeBinding returnType(ITypeBinding type,
			MethodDeclaration md) {
		return returnType(type, md.resolveBinding());
	}

	public static boolean returnCovariant(IMethodBinding mb, IMethodBinding mb2) {
		return !sameReturn(mb, mb2) && sameParameters(mb, mb2, true);
	}

	public static void printSignature(Transformer ctx, PrintWriter pw,
			ITypeBinding tb, IMethodBinding mb, DepInfo deps, boolean qualified) {
		printSignature(ctx, pw, tb, mb, mb.getReturnType(), deps, qualified);
	}

	public static void printSignature(Transformer ctx, PrintWriter pw,
			ITypeBinding type, IMethodBinding mb, ITypeBinding rt,
			DepInfo deps, boolean qualified) {
		if (mb.isConstructor()) {
			pw.print("void ");
			if (qualified) {
				pw.print(CName.qualified(type, true));
				pw.print("::");
			}

			pw.print(CName.CTOR + "(");
			String sep = printEnumCtorParams(ctx, pw, type, "", deps);
			if (mb.getParameterTypes().length > 0) {
				pw.print(sep);
			}
		} else {
			deps.soft(rt);

			if (!qualified) {
				pw.print(methodModifiers(mb));
				pw.print(TransformUtil.relativeRef(rt, type, true));
			} else {
				pw.print(TransformUtil.qualifiedRef(rt, false));
			}

			pw.print(" ");

			if (qualified) {
				pw.print(CName.qualified(type, false));
				pw.print("::");
			}

			pw.print(CName.of(mb) + "(");
		}

		printParams(pw, type, mb, false, deps);
		pw.print(")");
	}

	public static String printExtraCtorParams(Transformer ctx, PrintWriter pw,
			ITypeBinding type, Collection<IVariableBinding> closures,
			DepInfo deps, boolean isDefaultInitCtor) {
		String sep = "";
		if (hasOuterThis(type)) {
			pw.print(outerThis(type));
			sep = ", ";
		}

		if (closures != null) {
			for (IVariableBinding closure : closures) {
				pw.print(sep
						+ varTypeCName(closure.getModifiers(),
								closure.getType(), type, deps) + " "
						+ CName.of(closure));
				sep = ", ";
			}
		}

		if (isDefaultInitCtor) {
			pw.print(sep + "const ::" + CName.DEFAULT_INIT_TAG + "&");
			sep = ", ";
		} else {
			sep = printEnumCtorParams(ctx, pw, type, sep, deps);
		}

		return sep;
	}

	public static String printEnumCtorParams(Transformer ctx, PrintWriter pw,
			ITypeBinding type, String sep, DepInfo deps) {
		if (type.isEnum()) {
			pw.print(sep + "::java::lang::String* name, int ordinal");
			deps.soft(ctx.resolve(String.class));
			return ", ";
		}

		return sep;
	}

	public static void printEmptyCtorCall(PrintWriter pw, ITypeBinding type) {
		pw.print(CName.CTOR + "(");
		TransformUtil.printEnumCtorCallParams(pw, type, "");
		pw.print(")");
	}

	public static String printEnumCtorCallParams(PrintWriter pw,
			ITypeBinding type, String sep) {
		if (type.isEnum()) {
			pw.print(sep + "name, ordinal");
			return ", ";
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

	// If a method is return covariant and the returned type
	// is a subclass of the declaring class, we get a circular
	// include dependency in C++ - resolve by making the return
	// type not covariant
	public static ITypeBinding returnType(ITypeBinding type, IMethodBinding b) {
		b = b.getMethodDeclaration();
		ITypeBinding rtb = b.getReturnType();
		if (rtb == null || rtb.isPrimitive()) {
			return rtb;
		}

		rtb = rtb.getErasure();
		type = type.getErasure();
		if (type.isSubTypeCompatible(rtb) || rtb.isEqualTo(type)) {
			return rtb; // Always safe
		}

		ITypeBinding dc = b.getDeclaringClass().getErasure();
		if (!isDep(type, rtb, new HashSet<ITypeBinding>())) {
			return rtb;
		}

		List<IMethodBinding> methods = TypeUtil.methods(
				TypeUtil.allBases(dc, null), TypeUtil.overrides(b));

		for (IMethodBinding mb2 : methods) {
			if (returnCovariant(b, mb2)) {
				if (rtb.isSubTypeCompatible(type)) {
					return type;
				}

				return returnType(type, mb2); // We can be a little covariant at
												// least
			}
		}

		return rtb;
	}

	/**
	 * Check if a is in the dependencies of type
	 */
	public static boolean isDep(ITypeBinding a, ITypeBinding type,
			Set<ITypeBinding> checked) {
		type = type.getErasure();
		if (!checked.add(type)) {
			return false; // Already seen this type
		}

		a = a.getErasure();

		List<ITypeBinding> bases = TypeUtil.allBases(type, null);
		// type depends on all its bases
		for (ITypeBinding tb : bases) {
			if (tb.getErasure().isEqualTo(a)) {
				return true;
			}

			if (isDep(a, tb, checked)) {
				return true;
			}
		}

		// type depends on its return type dependencies
		for (IMethodBinding mb : type.getDeclaredMethods()) {
			List<IMethodBinding> methods = TypeUtil.methods(bases,
					TypeUtil.overrides(mb));

			for (IMethodBinding mb2 : methods) {
				if (!mb2.isConstructor() && returnCovariant(mb, mb2)) {
					if (a.isSubTypeCompatible(mb.getMethodDeclaration()
							.getReturnType().getErasure())) {
						return true;
					}

					if (isDep(a, mb.getMethodDeclaration().getReturnType(),
							checked)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	public static boolean baseDeclared(Transformer ctx, ITypeBinding type,
			IMethodBinding mb) {
		return (Modifier.isAbstract(mb.getModifiers()) || type.isInterface())
				&& baseHasSame(mb, type, ctx.resolve(Object.class));
	}

	public static boolean needsEmptyCtor(boolean hasEmpty, boolean hasInit,
			ITypeBinding type) {
		if (hasEmpty)
			return false;
		if (hasInit)
			return true;
		if (same(type, Object.class))
			return true;
		return false;
	}

	public static String makeDefaultInitTag() {
		return "*static_cast< ::" + CName.DEFAULT_INIT_TAG + "* >(0)";
	}

	public static boolean isValueOf(IMethodBinding mb, ITypeBinding type) {
		return type.getErasure().isEqualTo(mb.getReturnType().getErasure())
				&& mb.getName().equals("valueOf")
				&& mb.getParameterTypes().length == 1
				&& same(mb.getParameterTypes()[0], String.class);
	}

	public static boolean isValues(IMethodBinding mb, ITypeBinding type) {
		return mb.getReturnType().isArray()
				&& type.getErasure().isEqualTo(
						mb.getReturnType().getComponentType().getErasure())
				&& mb.getName().equals("values")
				&& mb.getParameterTypes().length == 0;
	}

	/** Check if this fragment should be initialized in init/clinit */
	public static boolean initInInit(VariableDeclarationFragment fragment) {
		if (!(fragment.getParent() instanceof FieldDeclaration)) {
			return false;
		}

		if (fragment.getInitializer() == null) {
			return false;
		}

		if (TransformUtil.constexprValue(fragment) != null) {
			return false;
		}

		return true;
	}
}
