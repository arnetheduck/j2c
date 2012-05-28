package se.arnetheduck.j2c.transform;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Modifier;
import java.text.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IInitializer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
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
	public static final String PUBLIC = "public:";
	public static final String PROTECTED = "public: /* protected */";
	public static final String PACKAGE = "public: /* package */";
	public static final String PRIVATE = "private:";

	public static final String NATIVE = "-native";
	public static final String STUB = "-stub";

	/** Name of fake static initializer */
	public static final String STATIC_INIT = "clinit";
	/** Name of fake instance initializer */
	public static final String INSTANCE_INIT = "init";
	/** Name of fake constructor */
	public static final String CTOR = "ctor";

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
			"unsigned", "using", "wchar_t", "xor", "xor_eq",
			TransformUtil.CTOR, TransformUtil.INSTANCE_INIT,
			TransformUtil.STATIC_INIT);

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
				+ cname(pkg == null ? name(tb)
						: (pkg.getName() + "." + name(tb)));
	}

	public static String relativeCName(ITypeBinding tb, ITypeBinding root,
			boolean global) {
		IPackageBinding pkg = elementPackage(tb);
		if (pkg == null) {
			return name(tb);
		}

		IPackageBinding rootPkg = elementPackage(root);
		if (rootPkg == null || !rootPkg.getKey().equals(pkg.getKey())) {
			return qualifiedCName(tb, global);
		}

		return name(tb);
	}

	private static IPackageBinding elementPackage(ITypeBinding tb) {
		// When processing generics, only the erasure will have a package
		return tb.isArray() ? tb.getElementType().getErasure().getPackage()
				: tb.getErasure().getPackage();
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

		return tbe.getName();
	}

	public static String name(IMethodBinding mb) {
		// private methods mess up using statements that import methods
		// from base classes
		String name = keywords(mb.getName());
		return Modifier.isPrivate(mb.getModifiers()) ? "_" + name : name;
	}

	public static String name(IVariableBinding vb) {
		// Methods and variables can have the same name so we postfix all
		// variables
		return vb.getName() + "_";
	}

	public static String[] packageName(ITypeBinding tb) {
		IPackageBinding pkg = elementPackage(tb);
		return pkg == null ? new String[0] : pkg.getNameComponents();
	}

	public static String qualifiedName(ITypeBinding tb) {
		IPackageBinding pkg = elementPackage(tb);
		return pkg == null ? name(tb) : (pkg.getName() + "." + name(tb));
	}

	public static String include(ITypeBinding tb) {
		return include(headerName(tb));
	}

	public static String include(String s) {
		return "#include \"" + s + "\"";
	}

	public static String headerName(ITypeBinding tb) {
		return qualifiedName(tb) + ".h";
	}

	public static String implName(ITypeBinding tb, String suffix) {
		return qualifiedName(tb) + suffix + ".cpp";
	}

	public static String mainName(ITypeBinding tb) {
		return qualifiedName(tb) + "-main.cpp";
	}

	public static Object constantValue(VariableDeclarationFragment node) {
		IVariableBinding vb = node.resolveBinding();
		ITypeBinding tb = vb.getType();

		Expression initializer = node.getInitializer();
		Object v = initializer == null ? null : initializer
				.resolveConstantExpressionValue();
		if (isConstExpr(tb) && isStatic(vb)
				&& Modifier.isFinal(vb.getModifiers()) && v != null) {
			return checkConstant(v);
		}

		return null;
	}

	public static Object constantValue(IVariableBinding vb) {
		ITypeBinding tb = vb.getType();

		Object v = vb.getConstantValue();
		if (isConstExpr(tb) && isStatic(vb)
				&& Modifier.isFinal(vb.getModifiers()) && v != null) {
			return checkConstant(v);
		}

		return null;
	}

	public static Object checkConstant(Object cv) {
		if (cv instanceof Character) {
			char ch = (char) cv;
			if ((ch < ' ')) {
				return (int) ch;
			}

			if (ch == '\'') {
				return "u'\\''";
			}

			if (ch == '\\') {
				return "u'\\\\'";
			}

			if (ch >= 0xd800 && ch <= 0xdfff) {
				return (int) ch;
			}

			if (ch > 127) {
				return String.format("u'\\u%04x'", (int) ch);
			}

			return "u'" + ch + "'";
		} else if (cv instanceof Integer) {
			if ((int) cv == Integer.MIN_VALUE) {
				// In C++, the part after '-' is parsed first which overflows
				// so we do a trick
				return "(-0x7fffffff-1)";
			}

			return cv;
		} else if (cv instanceof Long) {
			if ((long) cv == Long.MIN_VALUE) {
				// In C++, the part before '-' is parsed first which overflows
				// so we do a trick
				return "(-0x7fffffffffffffffLL-1)";
			}

			return cv + "ll";
		} else if (cv instanceof Float) {
			return cv + "f";
		}

		return cv;
	}

	private static boolean isConstExpr(ITypeBinding tb) {
		return tb.getName().equals("int") || tb.getName().equals("char")
				|| tb.getName().equals("long") || tb.getName().equals("byte")
				|| tb.getName().equals("short");
	}

	public static String fieldModifiers(ITypeBinding type, int modifiers,
			boolean header, boolean hasInitializer) {
		String ret = "";
		if (header && (Modifier.isStatic(modifiers) || type.isInterface())) {
			ret += "static ";
		}

		if (hasInitializer
				&& (Modifier.isFinal(modifiers) || type.isInterface())) {
			ret += "const ";
		}

		return ret;
	}

	public static String methodModifiers(int modifiers, int typeModifiers) {
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

		return "/*  throws(" + toCSV(parameters) + ") */";
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

		if (dep.isPrimitive()) {
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

	public static String printAccess(PrintWriter out, int access,
			String lastAccess) {
		if ((access & Modifier.PRIVATE) > 0) {
			if (!PRIVATE.equals(lastAccess)) {
				lastAccess = PRIVATE;
				out.println(lastAccess);
			}
		} else if ((access & Modifier.PROTECTED) > 0) {
			if (!PROTECTED.equals(lastAccess)) {
				lastAccess = PROTECTED;
				out.println(lastAccess);
			}
		} else if ((access & Modifier.PUBLIC) > 0) {
			if (!PUBLIC.equals(lastAccess)) {
				lastAccess = PUBLIC;
				out.println(lastAccess);
			}
		} else {
			if (!PACKAGE.equals(lastAccess)) {
				lastAccess = PACKAGE;
				out.println(lastAccess);
			}
		}

		return lastAccess;
	}

	public static String ref(ITypeBinding tb) {
		return tb.isPrimitive() ? "" : "*";
	}

	public static String ref(Type t) {
		return t.isPrimitiveType() ? "" : "*";
	}

	public static boolean isStatic(ITypeBinding tb) {
		return Modifier.isStatic(tb.getModifiers());
	}

	public static boolean isStatic(IMethodBinding mb) {
		return Modifier.isStatic(mb.getModifiers());
	}

	public static boolean isStatic(IVariableBinding vb) {
		return Modifier.isStatic(vb.getModifiers());
	}

	public static boolean isInner(ITypeBinding tb) {
		return tb.isNested() && !isStatic(tb);
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

	public static boolean outerStatic(ITypeBinding tb) {
		if (tb.isLocal()) {
			IMethodBinding mb = tb.getDeclaringMethod();
			if (mb == null) {
				IJavaElement je = tb.getJavaElement();

				try {
					while (je != null) {
						if (je instanceof IInitializer) {
							return Flags.isStatic(((IInitializer) je)
									.getFlags());
						}

						if (je instanceof IField) {
							return Flags.isStatic(((IField) je).getFlags());
						}

						je = je.getParent();
					}
				} catch (JavaModelException e) {
					throw new Error(e);
				}

				return false;
			}

			return isStatic(mb);
		}

		return isStatic(tb.getDeclaringClass());
	}

	/** Filter out C++ keywords */
	public static String keywords(String name) {
		if (keywords.contains(name)) {
			return name + "_";
		}

		return name;
	}

	public static String methodUsing(IMethodBinding mb) {
		if (Modifier.isPrivate(mb.getModifiers())) {
			return null;
		}

		ITypeBinding tb = mb.getDeclaringClass();

		ITypeBinding using = null;
		for (IMethodBinding b : baseMethods(tb, mb.getName())) {
			if (mb.getParameterTypes().length != b.getParameterTypes().length) {
				using = b.getDeclaringClass();
				break;
			}

			for (int i = 0; i < mb.getParameterTypes().length && using == null; ++i) {
				if (!mb.getParameterTypes()[i].getQualifiedName().equals(
						b.getParameterTypes()[i].getQualifiedName())) {
					using = b.getDeclaringClass();
				}
			}

			if (using != null) {
				break;
			}
		}

		if (using != null) {
			return "using " + relativeCName(using, tb, false) + "::" + name(mb)
					+ ";";
		}

		return null;
	}

	public static List<IMethodBinding> baseMethods(ITypeBinding tb, String name) {
		List<IMethodBinding> ret = new ArrayList<IMethodBinding>();

		// Order significant in case an interface is inherited multiple times
		Collection<ITypeBinding> bases = getAllBases(tb);
		for (ITypeBinding base : bases) {
			for (IMethodBinding mb : base.getDeclaredMethods()) {
				if (mb.getName().equals(name)) {
					ret.add(mb);
				}
			}
		}

		return ret;
	}

	public static String indent(int n) {
		String ret = "";
		for (int i = 0; i < n; i++)
			ret += "    ";
		return ret;
	}

	public static String virtual(ITypeBinding tb) {
		if (tb.isInterface()
				|| tb.getQualifiedName().equals(Object.class.getName())) {
			return "virtual ";
		}

		return "";
	}

	public static PrintWriter openHeader(IPath root, ITypeBinding tb)
			throws IOException {

		FileOutputStream fos = new FileOutputStream(root.append(
				TransformUtil.headerName(tb)).toFile());

		PrintWriter pw = new PrintWriter(fos);

		pw.println("// Generated from " + tb.getJavaElement().getPath());

		pw.println("#pragma once");
		pw.println();

		pw.println(include("forward.h"));
		pw.println();

		return pw;
	}

	public static PrintWriter openImpl(IPath root, ITypeBinding tb,
			String suffix) throws IOException {

		FileOutputStream fos = new FileOutputStream(root.append(
				TransformUtil.implName(tb, suffix)).toFile());

		PrintWriter pw = new PrintWriter(fos);

		pw.println("// Generated from " + tb.getJavaElement().getPath());

		pw.println(include(tb));
		pw.println();

		return pw;
	}

	public static List<ITypeBinding> getBases(ITypeBinding tb,
			ITypeBinding object) {
		List<ITypeBinding> ret = new ArrayList<ITypeBinding>();

		addDep(tb.getSuperclass(), ret);

		for (ITypeBinding ib : tb.getInterfaces()) {
			addDep(ib, ret);
		}

		if (ret.isEmpty()
				&& !tb.getQualifiedName().equals(Object.class.getName())) {
			addDep(object, ret);
		}

		return ret;
	}

	public static List<ITypeBinding> getAllBases(ITypeBinding tb) {
		List<ITypeBinding> ret = new ArrayList<ITypeBinding>();

		addDep(tb.getSuperclass(), ret);
		if (tb.getSuperclass() != null) {
			for (ITypeBinding a : getAllBases(tb.getSuperclass())) {
				addDep(a, ret);
			}
		}

		for (ITypeBinding ib : tb.getInterfaces()) {
			addDep(ib, ret);

			for (ITypeBinding a : getAllBases(ib)) {
				addDep(a, ret);
			}
		}

		return ret;
	}

	public static List<ITypeBinding> getBases(AST ast, ITypeBinding tb) {
		return getBases(tb, ast.resolveWellKnownType(Object.class.getName()));
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
			IMethodBinding mb, Transformer ctx) {
		out.print("(");
		for (int i = 0; i < mb.getParameterTypes().length; ++i) {
			if (i > 0)
				out.print(", ");

			ITypeBinding pb = mb.getParameterTypes()[i];
			ctx.softDep(pb);

			out.print(relativeCName(pb, tb, true));
			out.print(" ");
			out.print(ref(pb));
			out.print("a" + i);
		}

		out.print(")");
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
		if (isStatic(mb)) {
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

	public static boolean isBridged(IMethodBinding mb) {
		return needsBridge(mb, getSuperMethod(mb));
	}

	public static void declareBridge(PrintWriter pw, ITypeBinding tb,
			IMethodBinding mb, Transformer ctx) {
		if (!mb.isConstructor()) {
			IMethodBinding mb2 = getSuperMethod(mb);
			if (needsBridge(mb, mb2)) {
				mb2 = mb2.getMethodDeclaration();

				pw.print(TransformUtil.indent(1));

				TransformUtil.printSignature(pw, tb, mb2, ctx, false);

				pw.println(";");
			}
		}
	}

	public static List<ITypeBinding> defineBridge(PrintWriter pw,
			ITypeBinding tb, IMethodBinding mb, Transformer ctx) {
		List<ITypeBinding> deps = new ArrayList<ITypeBinding>();
		if (!mb.isConstructor()) {
			IMethodBinding mb2 = getSuperMethod(mb);
			if (needsBridge(mb, mb2)) {
				mb2 = mb2.getMethodDeclaration();

				printSignature(pw, tb, mb2, ctx, true);

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
						pw.print("a" + i);
						pw.print(")");
					} else {
						pw.print("a" + i);
					}
				}

				pw.println(");");
				pw.println("}");
				pw.println("");
			}
		}

		return deps;
	}

	public static boolean needsBridge(IMethodBinding mb, IMethodBinding mb2) {
		return mb2 != null && !sameSignature(mb, mb2.getMethodDeclaration())
				&& !returnCovariant(mb, mb2.getMethodDeclaration());
	}

	public static boolean sameSignature(IMethodBinding mb, IMethodBinding mb2) {
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

	private static boolean sameParameters(IMethodBinding mb, IMethodBinding mb2) {
		if (mb.getParameterTypes().length != mb2.getParameterTypes().length) {
			return false;
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
		return !sameReturn(mb, mb2) && sameParameters(mb, mb2);
	}

	public static void printSignature(PrintWriter pw, ITypeBinding tb,
			IMethodBinding mb, Transformer ctx, boolean qualified) {
		if (mb.isConstructor()) {
			pw.print(name(tb));
		} else {
			ITypeBinding rt = mb.getReturnType();
			ctx.softDep(rt);

			if (!qualified) {
				pw.print(methodModifiers(mb.getModifiers(), tb.getModifiers()));
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

		printParams(pw, tb, mb, ctx);
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
				&& mb.getParameterTypes()[0].getComponentType()
						.getQualifiedName().equals("java.lang.String");
	}

	public static boolean hasNatives(ITypeBinding tb) {
		for (IMethodBinding mb : tb.getDeclaredMethods()) {
			if (Modifier.isNative(mb.getModifiers())) {
				return true;
			}
		}

		return false;
	}

	public static void printStringSupport(ITypeBinding tb, PrintWriter pw) {
		if (!tb.getQualifiedName().equals("java.lang.String")) {
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

		pw.println("java::lang::String *lit(const char16_t *chars);");
		pw.println();
	}
}
