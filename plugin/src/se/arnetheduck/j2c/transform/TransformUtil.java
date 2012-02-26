package se.arnetheduck.j2c.transform;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Modifier;
import java.text.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IInitializer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
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

	public static String cname(String jname) {
		return jname.replace(".", "::");
	}

	public static String qualifiedCName(ITypeBinding tb) {
		IPackageBinding pkg = elementPackage(tb);
		return cname(pkg == null ? name(tb) : (pkg.getName() + "." + name(tb)));
	}

	public static String relativeCName(ITypeBinding tb, ITypeBinding root) {
		IPackageBinding pkg = elementPackage(tb);
		if (pkg == null) {
			return name(tb);
		}

		IPackageBinding rootPkg = elementPackage(root);
		if (rootPkg == null || !rootPkg.getKey().equals(pkg.getKey())) {
			return qualifiedCName(tb);
		}

		return name(tb);
	}

	private static IPackageBinding elementPackage(ITypeBinding tb) {
		return tb.isArray() ? tb.getElementType().getErasure().getPackage()
				: tb.getErasure().getPackage();
	}

	public static String name(ITypeBinding tb) {
		if (tb.isArray()) {
			return name(tb.getComponentType()) + "Array";
		}

		if (tb.isAnonymous()) {
			String c = tb.getDeclaringClass() == null ? "c" + tb.hashCode()
					: name(tb.getDeclaringClass());
			String m = tb.getDeclaringMethod() == null ? "m" + tb.hashCode()
					: tb.getDeclaringMethod().getName();

			if (tb.isLocal()) {
				// We use a hack here to avoid getting the same name for two
				// local classes
				String key = tb.getKey();
				Matcher x = Pattern.compile("\\$([^;]*);$").matcher(key);
				if (x.find()) {
					return c + "_" + m + x.group(1);
				}
			}
			return c + "_" + m;
		}

		if (tb.isNested()) {
			return name(tb.getDeclaringClass()) + "_"
					+ tb.getErasure().getName();
		}

		if (tb.isPrimitive()) {
			return primitive(tb.getName());
		}

		return tb.getErasure().getName();
	}

	public static String[] packageName(ITypeBinding tb) {
		IPackageBinding pkg = tb.isArray() ? tb.getElementType().getPackage()
				: tb.getPackage();
		return pkg == null ? new String[0] : pkg.getNameComponents();
	}

	public static String qualifiedName(ITypeBinding tb) {
		IPackageBinding pkg = tb.isArray() ? tb.getElementType().getPackage()
				: tb.getPackage();
		return pkg == null ? name(tb) : (pkg.getName() + "." + name(tb));
	}

	public static String include(ITypeBinding tb) {
		return include(headerName(tb));
	}

	public static String include(IPackageBinding pb) {
		return include(headerName(pb));
	}

	public static String include(String s) {
		return "#include \"" + s + "\"";
	}

	public static String include(Type t) {
		return include(headerName(t));
	}

	public static String headerName(ITypeBinding tb) {
		return qualifiedName(tb) + ".h";
	}

	public static String headerName(IPackageBinding pb) {
		return pb.getName() + ".h";
	}

	public static String headerName(Type t) {
		return headerName(t.resolveBinding());
	}

	public static String implName(ITypeBinding tb) {
		return qualifiedName(tb) + ".cpp";
	}

	public static String objName(ITypeBinding tb) {
		return qualifiedName(tb) + ".o";
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
		if (isConstExpr(tb) && Modifier.isStatic(vb.getModifiers())
				&& Modifier.isFinal(vb.getModifiers()) && v != null) {
			return checkConstant(v);
		}

		return null;
	}

	public static Object constantValue(IVariableBinding vb) {
		ITypeBinding tb = vb.getType();

		Object v = vb.getConstantValue();
		if (isConstExpr(tb) && Modifier.isStatic(vb.getModifiers())
				&& Modifier.isFinal(vb.getModifiers()) && v != null) {
			return checkConstant(v);
		}

		return null;
	}

	public static Object checkConstant(Object cv) {
		if (cv instanceof Character) {
			char ch = (char) cv;
			if ((ch < ' ') || ch > 127) {
				return (int) ch;
			}

			if (ch == '\'') {
				return "'\\''";
			}

			return "'" + ch + "'";
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

	public static String fieldModifiers(int modifiers, boolean header,
			boolean hasInitializer) {
		String ret = "";
		if (header && Modifier.isStatic(modifiers)) {
			ret += "static ";
		}

		if (Modifier.isFinal(modifiers) && hasInitializer) {
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

	public static String variableModifiers(int modifiers) {
		return fieldModifiers(modifiers, false, false);
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

	public static String primitive(String primitve) {
		return primitive(PrimitiveType.toCode(primitve));
	}

	public static String primitive(PrimitiveType.Code code) {
		if (code == PrimitiveType.BOOLEAN) {
			return "bool";
		} else if (code == PrimitiveType.BYTE) {
			return "int8_t";
		} else if (code == PrimitiveType.CHAR) {
			return "wchar_t";
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

		if (dep.isNullType()) {
			return false;
		}

		if (dep.isPrimitive()) {
			return false;
		}

		dep = dep.getErasure();

		return deps.add(dep);
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

	public static boolean isInner(ITypeBinding tb) {
		return tb.isNested() && !Modifier.isStatic(tb.getModifiers());
	}

	public static String outerThis(ITypeBinding tb) {
		return TransformUtil.relativeCName(tb.getDeclaringClass(), tb) + " *"
				+ outerThisName(tb);
	}

	public static String outerThisName(ITypeBinding tb) {
		return thisName(tb.getDeclaringClass());
	}

	public static String thisName(ITypeBinding tb) {
		return TransformUtil.name(tb) + "_this";
	}

	public static boolean outerStatic(ITypeBinding tb) {
		if (tb.isLocal()) {
			if (tb.getDeclaringMethod() == null) {
				IJavaElement je = tb.getJavaElement();
				while (je != null && !(je instanceof IInitializer)) {
					je = je.getParent();
				}

				if (je instanceof IInitializer) {
					try {
						return Flags.isStatic(((IInitializer) je).getFlags());
					} catch (JavaModelException e) {
						throw new Error(e);
					}
				}

				return false;
			}

			return Modifier.isStatic(tb.getDeclaringMethod().getModifiers());

		}

		return Modifier.isStatic(tb.getDeclaringClass().getModifiers());
	}

	private static Collection<String> keywords = Arrays.asList("delete",
			"register", "union");

	/** Filter out C++ keywords */
	public static String keywords(String name) {
		if (keywords.contains(name)) {
			return name + "_";
		}

		return name;
	}

	public static String methodUsing(IMethodBinding mb) {
		ITypeBinding tb = mb.getDeclaringClass();

		ITypeBinding using = null;
		for (IMethodBinding b : methods(tb.getSuperclass(), mb.getName())) {
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
			return "using " + relativeCName(using, tb) + "::" + mb.getName()
					+ ";";
		}

		return null;
	}

	public static List<IMethodBinding> methods(ITypeBinding tb, String name) {
		if (tb == null) {
			return Collections.emptyList();
		}

		List<IMethodBinding> ret = new ArrayList<IMethodBinding>();
		for (IMethodBinding mb : tb.getDeclaredMethods()) {
			if (mb.getName().equals(name)) {
				ret.add(mb);
			}
		}

		ret.addAll(methods(tb.getSuperclass(), name));
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

		pw.println(TransformUtil.include("forward.h"));
		pw.println();

		return pw;
	}

	public static PrintWriter openImpl(IPath root, ITypeBinding tb)
			throws IOException {

		FileOutputStream fos = new FileOutputStream(root.append(
				TransformUtil.implName(tb)).toFile());

		PrintWriter pw = new PrintWriter(fos);

		pw.println("// Generated from " + tb.getJavaElement().getPath());

		pw.println(TransformUtil.include(tb));
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
			ret.add(object);
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

	public static void printParams(PrintWriter pw, ITypeBinding tb,
			IMethodBinding mb, Transformer ctx) {
		pw.print("(");
		for (int i = 0; i < mb.getParameterTypes().length; ++i) {
			if (i > 0)
				pw.print(", ");

			ITypeBinding pb = mb.getParameterTypes()[i];
			ctx.softDep(pb);

			pw.print(relativeCName(pb, tb));
			pw.print(" ");
			pw.print(ref(pb));
			pw.print("a" + i);
		}

		pw.print(")");
	}

	public static IMethodBinding getSuperMethod(IMethodBinding mb) {
		for (ITypeBinding tb : mb.getDeclaringClass().getInterfaces()) {
			for (IMethodBinding mb2 : tb.getDeclaredMethods()) {
				if (mb.overrides(mb2)) {
					return mb2;
				}
			}
		}

		return null;
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

	public static void defineBridge(PrintWriter pw, ITypeBinding tb,
			IMethodBinding mb, Transformer ctx) {
		if (!mb.isConstructor()) {
			IMethodBinding mb2 = getSuperMethod(mb);
			if (needsBridge(mb, mb2)) {
				mb2 = mb2.getMethodDeclaration();

				printSignature(pw, tb, mb2, ctx, true);

				pw.println();
				pw.println("{ ");
				pw.print(indent(1));
				if (mb.getReturnType() != null
						&& !mb.getReturnType().getName().equals("void")) {
					pw.print("return ");
				}

				pw.print(keywords(mb2.getMethodDeclaration().getName()));
				pw.print("(");
				for (int i = 0; i < mb2.getParameterTypes().length; ++i) {
					if (i > 0)
						pw.print(", ");
					ITypeBinding pb = mb.getParameterTypes()[i];
					ITypeBinding pb2 = mb2.getParameterTypes()[i];

					if (!pb.isEqualTo(pb2)) {
						ctx.hardDep(tb);
						pw.print("dynamic_cast<");
						pw.print(relativeCName(pb, tb));
						pw.print(ref(pb));
						pw.print(">(");
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
		return mb.getReturnType().getErasure()
				.isEqualTo(mb2.getReturnType().getErasure());

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
				pw.print(relativeCName(rt, tb));
			} else {
				pw.print(qualifiedCName(rt));
			}

			pw.print(" ");
			pw.print(ref(rt));

			if (qualified) {
				pw.print(qualifiedCName(tb));
				pw.print("::");
			}

			pw.print(keywords(mb.getMethodDeclaration().getName()));
		}

		printParams(pw, tb, mb, ctx);
	}

	public static void printMain(PrintWriter pw, IMethodBinding mb,
			Transformer ctx) {
		if (mb.getReturnType() != null
				&& mb.getReturnType().getName().equals("void")
				&& mb.getName().equals("main")
				&& mb.getParameterTypes().length == 1
				&& mb.getParameterTypes()[0].isArray()
				&& mb.getParameterTypes()[0].getComponentType()
						.getQualifiedName().equals("java.lang.String")) {
			ctx.mains.add(mb.getDeclaringClass());
			pw.println("int main(int, char**)");
			pw.println("{");
			pw.print(indent(1));
			pw.print(qualifiedCName(mb.getDeclaringClass()));
			pw.println("::main(0);");
			pw.print(indent(1));
			pw.println("return 0;");
			pw.print("}");
			pw.println();
		}
	}

}
