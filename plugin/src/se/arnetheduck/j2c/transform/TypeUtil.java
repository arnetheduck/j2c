package se.arnetheduck.j2c.transform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

/** Utilities for traversing the type hierarchy */
public class TypeUtil {
	public static Predicate<Object> named(final String name) {
		return new Predicate<Object>() {
			@Override
			public boolean apply(Object t) {
				if (t instanceof ITypeBinding) {
					ITypeBinding tb = (ITypeBinding) t;
					return tb.getName().equals(name);
				}

				if (t instanceof IMethodBinding) {
					IMethodBinding mb = (IMethodBinding) t;
					return mb.getName().equals(name);
				}

				if (t instanceof IVariableBinding) {
					IVariableBinding vb = (IVariableBinding) t;
					return vb.getName().equals(name);
				}

				return false;
			}
		};
	}

	/** Predicate that returns true for any method that mb overrides */
	public static Predicate<IMethodBinding> overrides(final IMethodBinding mb) {
		return new Predicate<IMethodBinding>() {
			@Override
			public boolean apply(IMethodBinding t) {
				return !mb.isConstructor()
						&& mb.getMethodDeclaration().overrides(
								t.getMethodDeclaration());
			}
		};
	}

	/** tb and all bases */
	public static List<ITypeBinding> types(ITypeBinding tb, ITypeBinding object) {
		List<ITypeBinding> ret = new ArrayList<ITypeBinding>();
		ret.add(tb);
		ret.addAll(allBases(tb, object));
		return ret;
	}

	/** Superclasses of tb, including java.lang.Object but not tb itself */
	public static List<ITypeBinding> superClasses(ITypeBinding tb) {
		List<ITypeBinding> ret = new ArrayList<ITypeBinding>();
		superClasses(tb, ret);
		return ret;
	}

	public static void superClasses(ITypeBinding tb, Collection<ITypeBinding> c) {
		ITypeBinding superclass = tb.getSuperclass();
		if (superclass == null) {
			return;
		}

		c.add(superclass);
		superClasses(superclass, c);
	}

	/**
	 * The interfaces a particular type implements including recursive
	 * superinterfaces (but not the interfaces of any super class)
	 */
	public static List<ITypeBinding> interfaces(ITypeBinding tb) {
		List<ITypeBinding> ret = new ArrayList<ITypeBinding>();
		interfaces(tb, ret);
		return ret;
	}

	public static void interfaces(ITypeBinding tb, Collection<ITypeBinding> c) {
		for (ITypeBinding ib : tb.getInterfaces()) {
			c.add(ib);
			interfaces(ib, c);
		}
	}

	/** Union of all methods defined by the supplied types */
	public static List<IMethodBinding> methods(Collection<ITypeBinding> types) {
		return methods(types, null);
	}

	/** Union of all methods defined by the supplied types matching p */
	public static List<IMethodBinding> methods(Collection<ITypeBinding> types,
			Predicate<? super IMethodBinding> predicate) {

		List<IMethodBinding> ret = new ArrayList<IMethodBinding>();
		for (ITypeBinding tb : types) {
			for (IMethodBinding mb : tb.getDeclaredMethods()) {
				if (predicate == null || predicate.apply(mb)) {
					ret.add(mb);
				}
			}
		}

		return ret;
	}

	/** Direct bases (superclass and interfaces) of a type */
	public static List<ITypeBinding> bases(ITypeBinding tb, ITypeBinding object) {
		List<ITypeBinding> ret = new ArrayList<ITypeBinding>();

		if (tb.getSuperclass() != null) {
			ret.add(tb.getSuperclass());
		}

		for (ITypeBinding ib : tb.getInterfaces()) {
			ret.add(ib);
		}

		if (object != null && ret.isEmpty()
				&& !TransformUtil.same(tb, Object.class)) {
			ret.add(object);
		}

		return ret;
	}

	public static ITypeBinding commonBase(ITypeBinding tb0, ITypeBinding tb1,
			ITypeBinding object) {
		if (tb0.isEqualTo(tb1)) {
			return tb0;
		}

		if (tb0.isNullType()) {
			return tb1;
		}

		if (tb1.isNullType()) {
			return tb0;
		}

		List<ITypeBinding> b0 = types(tb0, object);
		List<ITypeBinding> b1 = types(tb1, object);
		for (ITypeBinding x0 : b0) {
			for (ITypeBinding x1 : b1) {
				if (x0.isEqualTo(x1)) {
					return x0;
				}
			}
		}

		throw new Error("Huh? Where's Object?");
	}

	/** Recursive bases (superclasses and interfaces) of a type */
	public static List<ITypeBinding> allBases(ITypeBinding tb,
			ITypeBinding object) {
		List<ITypeBinding> ret = new ArrayList<ITypeBinding>();

		List<ITypeBinding> supers = superClasses(tb);
		ret.addAll(supers);
		interfaces(tb, ret);

		for (ITypeBinding b : supers) {
			interfaces(b, ret);
		}

		if (object != null && !TransformUtil.same(tb, Object.class)
				&& !ret.contains(object)) {
			ret.add(object);
		}

		return ret;
	}

	public static boolean isClassLike(ITypeBinding type) {
		return type.isClass() || type.isEnum();
	}

	public static int countBases(ITypeBinding type, ITypeBinding base) {
		int ret = 0;

		base = base.getErasure();
		ITypeBinding superclass = type.getSuperclass();
		if (superclass != null) {
			superclass = superclass.getErasure();
			if (superclass.isEqualTo(base)) {
				return 1; // Base is a class, can only appear once
			}

			ret += countBases(superclass, base);
		}

		for (ITypeBinding ib : type.getInterfaces()) {
			ib = ib.getErasure();
			if (ib.isEqualTo(base)) {
				ret += 1;
			}

			ret += countBases(ib, base);
		}

		return ret;
	}
}
