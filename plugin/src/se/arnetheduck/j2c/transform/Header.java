package se.arnetheduck.j2c.transform;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

public class Header {
	public static final String PUBLIC = "public:";
	public static final String PROTECTED = "public: /* protected */";
	public static final String PACKAGE = "public: /* package */";
	public static final String PRIVATE = "private:";

	private static final String i1 = TransformUtil.indent(1);

	private final ITypeBinding type;
	private final Transformer ctx;

	private final DepInfo deps;

	private final List<IMethodBinding> constructors = new ArrayList<IMethodBinding>();
	private final Map<String, List<IMethodBinding>> methods = new TreeMap<String, List<IMethodBinding>>();
	private final List<IVariableBinding> fields = new ArrayList<IVariableBinding>();

	private String access;

	private PrintWriter out;

	public Header(Transformer ctx, ITypeBinding type, DepInfo deps) {
		this.ctx = ctx;
		this.type = type;
		this.deps = deps;
	}

	public void method(IMethodBinding mb) {
		if (mb.isConstructor()) {
			constructors.add(mb);
			return;
		}

		List<IMethodBinding> m = methods.get(mb.getName());
		if (m == null) {
			methods.put(mb.getName(), m = new ArrayList<IMethodBinding>());
		}

		m.add(mb);
	}

	public void field(IVariableBinding vb) {
		fields.add(vb);
	}

	public void write(IPath root, String body,
			Collection<IVariableBinding> closures, boolean hasClinit,
			boolean hasInit, Collection<ITypeBinding> nested, String access)
			throws IOException {

		this.access = access;
		String extras = getExtras(closures, hasClinit, hasInit, nested);

		try {
			out = FileUtil.open(TransformUtil.headerPath(root, type).toFile());

			println("// Generated from " + type.getJavaElement().getPath());
			println();

			println("#pragma once");
			println();

			boolean hasIncludes = false;

			if (type.getQualifiedName().equals(String.class.getName())) {
				println("#include <stddef.h>");
				hasIncludes = true;
			}

			if (deps.needsAtomic()) {
				println("#include <atomic>");
				hasIncludes = true;
			}

			List<ITypeBinding> bases = TypeUtil.bases(type,
					ctx.resolve(Object.class));

			Set<String> packages = new TreeSet<String>();
			packages.add(CName.packageOf(type));
			for (ITypeBinding tb : deps.getSoftDeps()) {
				packages.add(CName.packageOf(tb));
			}

			for (String p : packages) {
				println(TransformUtil.include(TransformUtil.packageHeader(
						ctx.getName(), p)));
				hasIncludes = true;
			}

			for (ITypeBinding dep : bases) {
				ctx.hardDep(dep);
				println(TransformUtil.include(dep));
				hasIncludes = true;
			}

			for (ITypeBinding dep : deps.getHardDeps()) {
				if (dep.isNullType() || dep.isPrimitive()
						|| dep.isEqualTo(type)) {
					continue;
				}

				if (!bases.contains(dep)) {
					println(TransformUtil.include(dep));
					hasIncludes = true;
				}
			}

			if (hasIncludes) {
				println();
			}

			printDefaultInitTag();

			print(type.isInterface() ? "struct " : "class ");

			print(CName.qualified(type, false));

			if (TransformUtil.isFinal(type)) {
				print(" final");
			}

			println();

			String sep = i1 + ": public ";

			for (ITypeBinding base : bases) {
				println(sep + TransformUtil.virtual(type, base)
						+ CName.relative(base, type, true));
				sep = i1 + ", public ";
			}

			println("{");

			printSuper(type);

			print(body);

			if (!extras.isEmpty()) {
				println();
				println(i1 + "// Generated");
			}

			print(extras);

			println("};");

			TransformUtil.printStringSupport(type, out);
		} finally {
			if (out != null) {
				out.close();
				out = null;
			}
		}
	}

	public static String initialAccess(ITypeBinding type) {
		return PUBLIC;
	}

	public static String printAccess(PrintWriter pw, IMethodBinding mb,
			String access) {
		if (mb.getDeclaringClass() != null
				&& (mb.getDeclaringClass().isInterface() || mb
						.getDeclaringClass().isAnnotation())) {
			return printAccess(pw, Modifier.PUBLIC, access);
		}

		return printAccess(pw, mb.getModifiers(), access);
	}

	private static String printAccess(PrintWriter pw, IVariableBinding vb,
			String access) {
		if (vb.getDeclaringClass() != null
				&& (vb.getDeclaringClass().isInterface() || vb
						.getDeclaringClass().isAnnotation())) {
			return printAccess(pw, Modifier.PUBLIC, access);
		}

		return printAccess(pw, vb.getModifiers(), access);
	}

	public static String printAccess(PrintWriter pw, int modifiers,
			String access) {
		if (Modifier.isPrivate(modifiers)) {
			if (!PRIVATE.equals(access)) {
				access = PRIVATE;
				pw.println();
				pw.println(access);
			}
		} else if (Modifier.isProtected(modifiers)) {
			if (!PROTECTED.equals(access)) {
				access = PROTECTED;
				pw.println();
				pw.println(access);
			}
		} else if (Modifier.isPublic(modifiers)) {
			if (!PUBLIC.equals(access)) {
				access = PUBLIC;
				pw.println();
				pw.println(access);
			}
		} else {
			if (!PACKAGE.equals(access)) {
				access = PACKAGE;
				pw.println();
				pw.println(access);
			}
		}

		return access;
	}

	public static String printProtected(PrintWriter pw, String access) {
		if (!"protected:".equals(access)) {
			access = "protected:";
			pw.println(access);
		}

		return access;
	}

	private void printSuper(ITypeBinding type) {
		if (type.getSuperclass() == null) {
			return;
		}
		access = printAccess(out, Modifier.PUBLIC, access);
		out.format(i1 + "typedef %s super;\n",
				CName.relative(type.getSuperclass(), type, true));
	}

	private void printClassLiteral() {
		access = printAccess(out, Modifier.PUBLIC, access);
		println(i1 + "static ::java::lang::Class *class_();");
	}

	/**
	 * In java, if a super class implements the method of an interface, it
	 * doesn't have to be re-implemented on the class implementing the
	 * interface. In C++ we have to forward the call to the super method - this
	 * method returns a list of methods needing such forwarding.
	 */
	public static List<IMethodBinding> baseCallMethods(ITypeBinding tb) {
		Set<IMethodBinding> im = new TreeSet<IMethodBinding>(
				new BindingComparator());

		im.addAll(TypeUtil.methods(TypeUtil.interfaces(tb), null));

		List<IMethodBinding> missing = new ArrayList<IMethodBinding>(im);

		for (IMethodBinding imb : im) {
			for (IMethodBinding mb : tb.getDeclaredMethods()) {
				if (TransformUtil.isSubsignature(mb, imb)) {
					missing.remove(imb);
					break;
				}
			}

			// Same method in two interfaces
			for (IMethodBinding mb : missing) {
				if (!mb.isEqualTo(imb) && TransformUtil.isSubsignature(mb, imb)) {
					missing.remove(imb);
					break;
				}
			}
		}
		return missing;
	}

	private String getExtras(Collection<IVariableBinding> closures,
			boolean hasClinit, boolean hasInit, Collection<ITypeBinding> nested) {
		StringWriter sw = new StringWriter();
		out = new PrintWriter(sw);

		printConstructors(hasInit, closures);
		printDefaultInitCtor(closures);

		printClassLiteral();
		printClinit(hasClinit);
		printInit(hasInit);
		printSuperCalls();
		printMethods();
		printClosures(closures);
		printFields();

		printEnumMethods();
		printDtor();
		printGetClass();

		printStringOperator();

		printFriends(nested);

		out.close();
		out = null;
		return sw.toString();
	}

	private void printDefaultInitTag() {
		if (!TypeUtil.isClassLike(type)) {
			return;
		}

		println("struct " + CName.DEFAULT_INIT_TAG + ";");
		println();
	}

	private void printDefaultInitCtor(Collection<IVariableBinding> closures) {
		if (!TypeUtil.isClassLike(type) || type.isAnonymous()) {
			return;
		}

		access = printProtected(out, access);

		print(i1 + CName.of(type) + "(");
		TransformUtil
				.printExtraCtorParams(ctx, out, type, closures, deps, true);
		println(");");
		println();
	}

	private void printStringOperator() {
		if (TransformUtil.same(type, String.class)) {
			println(i1
					+ "friend String *operator\"\" _j(const char16_t *s, size_t n);");
		}
	}

	private void printGetClass() {
		if (!TypeUtil.isClassLike(type)) {
			return;
		}

		access = printAccess(out, Modifier.PRIVATE, access);

		println(i1 + "virtual ::java::lang::Class* " + CName.GET_CLASS + "();");
	}

	private void printDtor() {
		if (TransformUtil.same(type, Object.class)) {
			access = printAccess(out, Modifier.PUBLIC, access);
			println(i1 + "virtual ~Object();");
		}
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
				hasValues |= isValues(mb);
			}
		}

		m = methods.get("valueOf");
		if (m != null) {
			for (IMethodBinding mb : m) {
				hasValueOf |= isValueOf(mb);
			}
		}

		for (IMethodBinding mb : type.getDeclaredMethods()) {
			if (!hasValues && isValues(mb)) {
				access = printAccess(out, Modifier.PUBLIC, access);
				print(i1);
				TransformUtil.printSignature(ctx, out, type, mb, deps, false);
				println(" { return nullptr; /* TODO */ }");
				hasValues = true;
			} else if (!hasValueOf && isValueOf(mb)) {
				access = printAccess(out, Modifier.PUBLIC, access);
				print(i1);
				TransformUtil.printSignature(ctx, out, type, mb, deps, false);
				println(" { return nullptr; /* TODO */ }");
				hasValueOf = true;
			}
		}

		return;
	}

	private boolean isValueOf(IMethodBinding mb) {
		return type.getErasure().isEqualTo(mb.getReturnType().getErasure())
				&& mb.getName().equals("valueOf")
				&& mb.getParameterTypes().length == 1
				&& TransformUtil.same(mb.getParameterTypes()[0], String.class);
	}

	private boolean isValues(IMethodBinding mb) {
		return mb.getReturnType().isArray()
				&& type.getErasure().isEqualTo(
						mb.getReturnType().getComponentType().getErasure())
				&& mb.getName().equals("values")
				&& mb.getParameterTypes().length == 0;
	}

	private void printConstructors(boolean hasInit,
			Collection<IVariableBinding> closures) {
		if (!TypeUtil.isClassLike(type)) {
			return;
		}

		String name = CName.of(type);

		if (type.isAnonymous()) {
			getAnonCtors(type, constructors);
		}

		boolean hasEmpty = false;
		boolean hasNonempty = false;
		for (IMethodBinding mb : constructors) {
			access = printAccess(out, mb, access);

			print(i1 + name + "(");

			String sep = TransformUtil.printExtraCtorParams(ctx, out, type,
					closures, deps, false);

			if (mb.getParameterTypes().length > 0) {
				print(sep);
				TransformUtil.printParams(out, type, mb, false, deps);
				hasNonempty = true;
			} else {
				hasEmpty = true;
			}

			println(");");
		}

		if (constructors.isEmpty()) {
			// No explicit constructor in the Java code, so per §8.8.9 a default
			// one should be generated, just like C++. We need it to make it
			// explicit to call the default_init_tag constructor to ensure that
			// clinit takes place
			access = printAccess(out, Modifier.PUBLIC, access);
			print(i1 + name + "(");

			TransformUtil.printExtraCtorParams(ctx, out, type, closures, deps,
					false);

			println(");");

			if (TransformUtil.needsEmptyCtor(hasEmpty, hasNonempty, hasInit,
					type)) {
				access = printProtected(out, access);
				print(i1 + "void " + CName.CTOR + "(");
				TransformUtil.printEnumCtorParams(ctx, out, type, "", deps);
				println(");");
			}
		}
	}

	public static void getAnonCtors(ITypeBinding type,
			Collection<IMethodBinding> constructors) {
		assert (constructors.isEmpty());
		for (IMethodBinding mb : type.getSuperclass().getDeclaredMethods()) {
			if (TransformUtil.asBaseConstructor(mb, type)) {
				constructors.add(mb);
			}
		}
	}

	private void printClinit(boolean hasClinit) {
		if (!TypeUtil.isClassLike(type)) {
			return;
		}

		if (!hasClinit && !TransformUtil.same(type, Object.class)) {
			return;
		}

		access = printAccess(out, Modifier.PUBLIC, access);
		println(i1 + "static void " + CName.STATIC_INIT + "();");
	}

	private void printInit(boolean hasInit) {
		if (!hasInit) {
			return;
		}

		access = printAccess(out, Modifier.PRIVATE, access);
		println(i1 + "void " + CName.INSTANCE_INIT + "();");
	}

	private void printMethods() {
		if (TypeUtil.isClassLike(type)) {
			for (List<IMethodBinding> e : methods.values()) {
				for (IMethodBinding mb : e) {
					access = TransformUtil.declareBridge(ctx, out, type, mb,
							deps, access);
				}
			}
		}

		List<IMethodBinding> superMethods = hiddenMethods(type, ctx, methods);

		// The remaining methods need unhiding - we don't use "using" as it
		// breaks if there's a private method with the same name in the base
		// class
		for (IMethodBinding mb : superMethods) {
			access = printAccess(out, mb.getModifiers(), access);
			print(i1);
			TransformUtil.printSignature(ctx, out, type,
					mb.getMethodDeclaration(), deps, false);
			if (Modifier.isAbstract(mb.getModifiers())) {
				print(" = 0");
			}

			println(";");
		}
	}

	public static List<IMethodBinding> hiddenMethods(ITypeBinding type,
			Transformer ctx, Map<String, List<IMethodBinding>> methods) {
		List<IMethodBinding> superMethods = TypeUtil.methods(TypeUtil.allBases(
				type, ctx.resolve(Object.class)));
		outer: for (Iterator<IMethodBinding> i = superMethods.iterator(); i
				.hasNext();) {
			IMethodBinding supermethod = i.next();

			if (Modifier.isPrivate(supermethod.getModifiers())
					|| supermethod.isConstructor()) {
				i.remove();
				continue;
			}

			Collection<IMethodBinding> declared = methods.get(supermethod
					.getName());

			if (declared == null) {
				i.remove();
				continue;
			}

			for (IMethodBinding d : declared) {
				if (TransformUtil.sameParameters(supermethod, d, false)) {
					i.remove();
					continue outer;
				}
			}
		}

		List<IMethodBinding> copy = new ArrayList<IMethodBinding>(superMethods);
		for (IMethodBinding a : copy) {
			for (Iterator<IMethodBinding> i = superMethods.iterator(); i
					.hasNext();) {
				IMethodBinding b = i.next();
				if (a.overrides(b)) {
					i.remove();
					continue;
				}
			}
		}

		copy = new ArrayList<IMethodBinding>(superMethods);
		for (IMethodBinding a : copy) {
			boolean dupe = false;
			for (Iterator<IMethodBinding> i = superMethods.iterator(); i
					.hasNext();) {
				IMethodBinding b = i.next();
				if (TransformUtil.isSubsignature(a, b)) {
					if (dupe) {
						i.remove();
					} else {
						dupe = true;
					}
				}
			}
		}

		return superMethods;
	}

	private void printSuperCalls() {
		if (type.isInterface()) {
			List<IMethodBinding> dupes = dupeNames(type);
			for (IMethodBinding dupe : dupes) {
				access = printAccess(out, Modifier.PUBLIC, access);

				print(i1);
				TransformUtil.printSignature(ctx, out, type,
						dupe.getMethodDeclaration(), dupe.getReturnType(),
						deps, false);
				println(" = 0;");
				method(dupe);
			}
		}

		if (!TypeUtil.isClassLike(type)) {
			return;
		}

		List<IMethodBinding> missing = baseCallMethods(type);
		for (IMethodBinding decl : missing) {
			IMethodBinding impl = findImpl(type, decl);
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
		// Interface methods are always public
		access = printAccess(out, Modifier.PUBLIC, access);

		print(i1);
		ITypeBinding irt = impl.getReturnType();
		TransformUtil.printSignature(ctx, out, type,
				decl.getMethodDeclaration(), irt, deps, false);

		if (Modifier.isAbstract(impl.getModifiers())) {
			print(" = 0");
		}

		println(";");

		method(decl);
		ITypeBinding irte = irt.getErasure();

		if (!irte.isEqualTo(decl.getMethodDeclaration().getReturnType()
				.getErasure())
				|| !irte.isEqualTo(impl.getMethodDeclaration().getReturnType()
						.getErasure())) {
			hardDep(irt);
		}
	}

	/**
	 * In C++, if a method with the same name exists in two base classes,
	 * ambiguity ensues even if the methods are overloads (name resolution comes
	 * before overload resolution). This method returns a list of such
	 * duplicates.
	 *
	 * @param type
	 * @return
	 */
	private List<IMethodBinding> dupeNames(ITypeBinding type) {
		List<IMethodBinding> ret = new ArrayList<IMethodBinding>();

		List<ITypeBinding> bases = TypeUtil.bases(type,
				ctx.resolve(Object.class));
		for (ITypeBinding b0 : bases) {
			b0 = b0.getErasure(); // This will get us erased method declarations
			for (ITypeBinding b1 : bases) {
				b1 = b1.getErasure();
				if (b0 == b1)
					continue;

				for (IMethodBinding m0 : b0.getDeclaredMethods()) {
					for (IMethodBinding m1 : b1.getDeclaredMethods()) {
						if (m0.getName().equals(m1.getName())) {
							boolean found = false;
							for (int i = 0; i < ret.size(); ++i) {
								IMethodBinding m2 = ret.get(i);
								if (TransformUtil.isSubsignature(m2, m0)) {
									found = true;

									// If two methods have different return
									// type, use the method with the most
									// derived return type for return covariance
									// to work properly
									if (m0.getReturnType()
											.getErasure()
											.isSubTypeCompatible(
													m2.getReturnType()
															.getErasure())) {
										hardDep(m0.getReturnType());
										m2 = ret.set(i, m0);
									}
								}
							}

							if (!found) {
								ret.add(m0);
							}
						}
					}
				}
			}
		}

		for (IMethodBinding mb : type.getDeclaredMethods()) {
			for (Iterator<IMethodBinding> i = ret.iterator(); i.hasNext();) {
				if (TransformUtil.isSubsignature(mb, i.next())) {
					i.remove();
				}
			}
		}

		return ret;
	}

	public static IMethodBinding findImpl(ITypeBinding type, IMethodBinding mb) {
		Collection<IMethodBinding> superMethods = TypeUtil.methods(TypeUtil
				.superClasses(type));

		for (IMethodBinding sm : superMethods) {
			if (TransformUtil.isSubsignature(sm, mb)) {
				return sm;
			}
		}

		return null;
	}

	private void printClosures(Collection<IVariableBinding> closures) {
		ITypeBinding sb = type.getSuperclass();
		boolean superInner = sb != null && TransformUtil.hasOuterThis(sb);
		if (TransformUtil.hasOuterThis(type)) {
			if (!superInner
					|| sb.getDeclaringClass() != null
					&& !type.getDeclaringClass().getErasure()
							.isEqualTo(sb.getDeclaringClass().getErasure())) {
				deps.soft(type.getDeclaringClass());
				println(i1 + TransformUtil.outerThis(type) + ";");
			}
		}

		if (closures != null) {
			for (IVariableBinding closure : closures) {
				deps.soft(closure.getType());
				println(i1
						+ TransformUtil.varTypeCName(closure.getModifiers(),
								closure.getType(), type, deps) + " "
						+ CName.of(closure) + ";");
			}
		}
	}

	private void printFields() {
		for (IVariableBinding vb : fields) {
			printField(vb);
		}
	}

	private void printField(IVariableBinding vb) {
		boolean asMethod = TransformUtil.asMethod(vb);
		if (asMethod) {
			access = printAccess(out, vb, access);
			out.format("%sstatic %s& %s();\n", i1, TransformUtil.varTypeCName(
					vb.getModifiers(), vb.getType(), type, deps), CName.of(vb));
		}
	}

	private void printFriends(Collection<ITypeBinding> nested) {
		if (type.isInterface()) {
			return; // Everything is public in these
		}

		for (ITypeBinding nb : nested) {
			deps.soft(nb);
			if (!nb.isEqualTo(type)) {
				println(i1 + "friend class " + CName.of(nb) + ";");
			}
		}
	}

	public void hardDep(ITypeBinding dep) {
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
