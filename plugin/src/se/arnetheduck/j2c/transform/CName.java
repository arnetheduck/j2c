package se.arnetheduck.j2c.transform;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

public class CName {
	/**
	 * Name of fake static initializer - method that intializes static fields
	 * and runs static initializer blocks
	 */
	public static final String STATIC_INIT = "clinit";

	/**
	 * Name of fake instance initializer - method that initializes fields and
	 * runs instance initializer blocks
	 */
	public static final String INSTANCE_INIT = "init";

	/**
	 * Name of fake constructor containing the body of the Java constructor,
	 * necessary to simulate Java initialization order
	 */
	public static final String CTOR = "ctor";

	/** Virtual method that returns the dynamic class of the current object */
	public static final String GET_CLASS = "getClass0";

	/**
	 * The default-init constructor takes care of the first phase of Java object
	 * initialization, namely to initialize all fields to 0, and of calling
	 * clinit to make sure the class is initialized.
	 */
	public static final String DEFAULT_INIT_TAG = "default_init_tag";

	/**
	 * Cast function that throws an appropriate exception if a cast fails
	 */
	public static final String JAVA_CAST = "java_cast";

	/**
	 * Null pointer check function - throws NPE if the passed pointer is null
	 */
	public static final String NPC = "npc";

	/** C++ keywords + special method names - java keywords */
	public static Collection<String> keywords = Arrays.asList("alignas",
			"alignof", "and", "and_eq", "asm", "auto", "bitand", "bitor",
			"bool", "char16_t", "char32_t", "compl", "const", "constexpre",
			"const_cast", "decltype", "delete", "dynamic_cast", "explicit",
			"export", "extern", "friend", "goto", "inline", "mutable",
			"namespace", "noexcept", "not", "not_eq", "nullptr", "operator",
			"or", "or_eq", "register", "reinterpret_cast", "signed", "sizeof",
			"static_assert", "static_cast", "struct", "template",
			"thread_local", "typedef", "typeid", "typename", "union",
			"unsigned", "using", "wchar_t", "xor", "xor_eq", CTOR,
			INSTANCE_INIT, STATIC_INIT, GET_CLASS, DEFAULT_INIT_TAG, JAVA_CAST,
			"int8_t", "int16_t", "int32_t", "int64_t", "char16_t");

	public static String qualified(ITypeBinding tb, boolean global) {
		IPackageBinding pkg = TransformUtil.elementPackage(tb);
		return (global && !tb.isPrimitive() ? "::" : "")
				+ (pkg == null ? of(tb) : (of(pkg) + "." + of(tb))).replace(
						".", "::");
	}

	public static String relative(ITypeBinding tb, ITypeBinding root,
			boolean global) {
		if (!samePackage(tb, root)) {
			return qualified(tb, global);
		}

		String tbn = of(tb);
		if (tbn.equals(Object.class.getSimpleName())
				&& !TransformUtil.same(tb, Object.class)) {
			// Intefaces have null superclass but inherit (conceptually) from
			// Object
			return qualified(tb, global);
		}

		// In C++, unqualified names in a class are looked up in base
		// classes before the own namespace
		List<ITypeBinding> bases = TypeUtil.allBases(root, null);
		for (ITypeBinding sb : bases) {
			if (tb.getErasure().isEqualTo(sb.getErasure())) {
				return tbn;
			}

			if (of(sb).equals(tbn)) {
				return qualified(tb, global);
			}
		}

		return tbn;
	}

	public static boolean samePackage(ITypeBinding tb0, ITypeBinding tb1) {
		IPackageBinding p0 = TransformUtil.elementPackage(tb0);
		IPackageBinding p1 = TransformUtil.elementPackage(tb1);
		if (p0 == null) {
			return p1 == null;
		}

		return p1 != null && p0.isEqualTo(p1);
	}

	private static Pattern bin = Pattern.compile("\\$\\d+");

	public static String of(ITypeBinding tb) {
		if (tb.isArray()) {
			return of(tb.getComponentType()) + "Array";
		}

		if (tb.isPrimitive()) {
			return TransformUtil.primitive(tb.getName());
		}

		ITypeBinding tbe = tb.getErasure();

		if (tbe.isLocal()) {
			StringBuilder ret = new StringBuilder();

			Matcher match = bin.matcher(tbe.getBinaryName());
			String sep = "";

			if (tbe.getDeclaringClass() != null) {
				ret.append(of(tbe.getDeclaringClass()));
				sep = "_";
			}

			if (tbe.getDeclaringMethod() != null
					&& tbe.getDeclaringMethod().getName().length() > 0) {
				ret.append(sep + tbe.getDeclaringMethod().getName());
				sep = "_";
			}

			if (tbe.getName() != null && tbe.getName().length() > 0) {
				ret.append(sep + tbe.getName());
				sep = "_";
			}

			while (match.find()) {
				ret.append(match.group(0).replaceAll("\\$", "_"));
			}

			return ret.toString();
		}

		if (tbe.isNested()) {
			return of(tbe.getDeclaringClass()) + "_" + tbe.getName();
		}

		return CName.keywords(tbe.getName());
	}

	public static String of(IMethodBinding mb) {
		// private methods mess up using statements that import methods
		// from base classes
		String ret = CName.keywords(mb.getName());
		ret = Modifier.isPrivate(mb.getModifiers()) ? "_" + ret : ret;

		IMethodBinding lastOverride = couldOverrideDefault(mb, ret);
		if (lastOverride != null) {
			ret = of(lastOverride.getDeclaringClass()) + "_" + ret;
		}

		// Methods can have the same name as the constructor without being a
		// constructor!
		if (!mb.isConstructor() && hasName(mb.getDeclaringClass(), ret)) {
			ret = "_" + ret;
		}

		return ret;
	}

	private static boolean hasName(ITypeBinding tb, String ret) {
		if (tb.getName().equals(ret)) {
			return true;
		}

		if (tb.getSuperclass() != null && hasName(tb.getSuperclass(), ret)) {
			return true;
		}

		for (ITypeBinding ib : tb.getInterfaces()) {
			if (hasName(ib, ret)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * In Java, if a method has the same name as a package-private method in a
	 * base class, it will not override the base class member. In C++, we have
	 * nothing of the sort so we have to rename the non-overriding method
	 */
	private static IMethodBinding couldOverrideDefault(IMethodBinding mb,
			String ret) {
		ITypeBinding tb = mb.getDeclaringClass();
		if (tb != null) {
			IMethodBinding last = mb;
			for (ITypeBinding tbx = tb.getSuperclass(); tbx != null; tbx = tbx
					.getSuperclass()) {
				boolean samePackage = samePackage(tb, tbx);

				for (IMethodBinding mbx : tbx.getDeclaredMethods()) {
					if (!samePackage
							&& TransformUtil.isDefault(mbx.getModifiers())
							&& mbx.getName().equals(mb.getName())
							&& TransformUtil.sameParameters(mb, mbx, true)) {
						return last;
					}

					if (last.overrides(mbx)) {
						// This will give the final name of the method
						last = mbx;
					}
				}
			}
		}

		return null;
	}

	public static String of(IVariableBinding vb) {
		// Methods and variables can have the same name so we postfix all
		// variables
		return vb.getName() + "_";
	}

	public static String of(IPackageBinding pb) {
		String[] n = pb.getNameComponents();
		StringBuilder ret = new StringBuilder();
		String sep = "";
		for (int i = 0; i < n.length; ++i) {
			ret.append(sep);
			sep = ".";
			ret.append(CName.keywords(n[i]));
		}

		return ret.toString();
	}

	public static String packageOf(ITypeBinding tb) {
		IPackageBinding pkg = TransformUtil.elementPackage(tb);
		return pkg == null ? "" : of(pkg);
	}

	/** Filter out C++ keywords and other reserved names */
	public static String keywords(String name) {
		if (name.endsWith("Array")) {
			String n = name;
			String prefix = "";
			while (n.endsWith("Array")) {
				prefix += "_";
				n = n.substring(0, n.length() - "Array".length());
			}

			return prefix + name;
		}

		if (keywords.contains(name)) {
			return "_" + name;
		}

		return name;
	}
}
