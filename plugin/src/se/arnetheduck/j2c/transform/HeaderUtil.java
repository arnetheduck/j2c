package se.arnetheduck.j2c.transform;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;

public class HeaderUtil {
	public static final String PUBLIC = "public:";
	public static final String PROTECTED = "public: /* protected */";
	public static final String PACKAGE = "public: /* package */";
	public static final String PRIVATE = "private:";

	private static final String i1 = TransformUtil.indent(1);

	public static PrintWriter open(IPath root, ITypeBinding type,
			Transformer ctx, Collection<ITypeBinding> softDeps,
			Collection<ITypeBinding> hardDeps) throws IOException {

		FileOutputStream fos = new FileOutputStream(root.append(
				TransformUtil.headerName(type)).toFile());

		PrintWriter pw = new PrintWriter(fos);

		pw.println("// Generated from " + type.getJavaElement().getPath());
		pw.println();

		pw.println("#pragma once");
		pw.println();

		if (type.getQualifiedName().equals(String.class.getName())) {
			pw.println("#include <stddef.h>");
		}

		List<ITypeBinding> bases = TransformUtil.getBases(type,
				ctx.resolve(Object.class));

		Set<String> packages = new TreeSet<String>();
		packages.add(TransformUtil.packageName(type));
		for (ITypeBinding tb : softDeps) {
			packages.add(TransformUtil.packageName(tb));
		}

		for (ITypeBinding tb : bases) {
			packages.remove(TransformUtil.packageName(tb));
		}

		boolean hasIncludes = false;

		for (String p : packages) {
			pw.println(TransformUtil.include(TransformUtil.packageHeader(p)));
			hasIncludes = true;
		}

		for (ITypeBinding dep : bases) {
			ctx.hardDep(dep);
			pw.println(TransformUtil.include(dep));
			hasIncludes = true;
		}

		for (ITypeBinding dep : hardDeps) {
			if (!bases.contains(dep)) {
				pw.println(TransformUtil.include(dep));
				hasIncludes = true;
			}
		}

		if (hasIncludes) {
			pw.println();
		}

		pw.print(type.isInterface() ? "struct " : "class ");

		pw.println(TransformUtil.qualifiedCName(type, false));

		String sep = i1 + ": public ";

		for (ITypeBinding base : bases) {
			pw.println(sep + TransformUtil.virtual(base)
					+ TransformUtil.relativeCName(base, type, true));
			sep = i1 + ", public ";
		}

		return pw;
	}

	public static String initialAccess(ITypeBinding type) {
		return type.isInterface() ? PUBLIC : PRIVATE;
	}

	public static String printAccess(PrintWriter out, int access,
			String lastAccess) {
		if ((access & Modifier.PRIVATE) > 0) {
			if (!PRIVATE.equals(lastAccess)) {
				lastAccess = PRIVATE;
				out.println();
				out.println(lastAccess);
			}
		} else if ((access & Modifier.PROTECTED) > 0) {
			if (!PROTECTED.equals(lastAccess)) {
				lastAccess = PROTECTED;
				out.println();
				out.println(lastAccess);
			}
		} else if ((access & Modifier.PUBLIC) > 0) {
			if (!PUBLIC.equals(lastAccess)) {
				lastAccess = PUBLIC;
				out.println();
				out.println(lastAccess);
			}
		} else {
			if (!PACKAGE.equals(lastAccess)) {
				lastAccess = PACKAGE;
				out.println();
				out.println(lastAccess);
			}
		}

		return lastAccess;
	}

	public static String printSuper(PrintWriter pw, ITypeBinding type,
			String lastAccess) {
		if (type.getSuperclass() == null) {
			return lastAccess;
		}

		lastAccess = printAccess(pw, Modifier.PRIVATE, lastAccess);
		pw.format(i1 + "typedef %s super;\n",
				TransformUtil.relativeCName(type.getSuperclass(), type, true));
		return lastAccess;
	}

	public static String printClassLiteral(PrintWriter pw, String lastAccess) {
		lastAccess = printAccess(pw, Modifier.PUBLIC, lastAccess);
		pw.println(i1 + "static ::java::lang::Class *class_();");
		return lastAccess;
	}

	public static String printGetClass(PrintWriter out, ITypeBinding type,
			String lastAccess) {
		if (!type.isClass()) {
			return lastAccess;
		}

		lastAccess = HeaderUtil.printAccess(out, Modifier.PRIVATE, lastAccess);

		out.println(i1 + "virtual ::java::lang::Class* "
				+ TransformUtil.GET_CLASS + "();");

		return lastAccess;
	}

	public static void printStringOperator(PrintWriter out, ITypeBinding type) {
		if (TransformUtil.same(type, String.class)) {
			out.println(i1
					+ "friend String *operator\"\" _j(const char16_t *s, size_t n);");
		}
	}

	/** Generate implicit enum methods */
	public static String printEnumMethods(PrintWriter out, ITypeBinding type,
			Collection<ITypeBinding> softDeps, String lastAccess) {
		if (!type.isEnum()) {
			return lastAccess;
		}

		lastAccess = HeaderUtil.printAccess(out, Modifier.PUBLIC, lastAccess);

		for (IMethodBinding mb : type.getDeclaredMethods()) {
			if (type.createArrayType(1).isEqualTo(mb.getReturnType())
					&& mb.getName().equals("values")
					&& mb.getParameterTypes().length == 0) {
				out.print(i1);
				TransformUtil.printSignature(out, type, mb, softDeps, false);
				out.println(" { return nullptr; /* TODO */ }");
			} else if (type.isEqualTo(mb.getReturnType())
					&& mb.getName().equals("valueOf")
					&& mb.getParameterTypes().length == 1
					&& mb.getParameterTypes()[0].getQualifiedName().equals(
							String.class.getName())) {
				out.print(i1);
				TransformUtil.printSignature(out, type, mb, softDeps, false);
				out.println(" { return nullptr; /* TODO */ }");
			}
		}

		return lastAccess;
	}

	public static String printClinit(PrintWriter out, String lastAccess) {
		lastAccess = HeaderUtil.printAccess(out, Modifier.PUBLIC, lastAccess);
		out.println(i1 + "static void " + TransformUtil.STATIC_INIT + "();");
		return lastAccess;
	}

	public static String printDtor(PrintWriter out, ITypeBinding type,
			String lastAccess) {
		if (type.getQualifiedName().equals(Object.class.getName())) {
			lastAccess = HeaderUtil.printAccess(out, Modifier.PUBLIC,
					lastAccess);
			out.println(i1 + "virtual ~Object();");
		}
		return lastAccess;
	}
}
