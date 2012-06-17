package se.arnetheduck.j2c.transform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;

/** Utilities for traversing the type hierarchy */
public class TypeUtil {
	/** Superclasses of tb, including java.lang.Object but not tb itself */
	public static List<ITypeBinding> superClasses(ITypeBinding tb) {
		assert (tb.isClass());
		if (tb.getSuperclass() == null) {
			return Collections.emptyList();
		}

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
		if (tb.getInterfaces().length == 0) {
			return Collections.emptyList();
		}

		List<ITypeBinding> ret = new ArrayList<ITypeBinding>();
		interfaces(tb, ret);
		return ret;
	}

	public static void interfaces(ITypeBinding tb, Collection<ITypeBinding> c) {
		for (ITypeBinding ib : tb.getInterfaces()) {
			c.add(ib);
			c.addAll(interfaces(ib));
		}
	}

	/** Union of all methods defined by the supplied types */
	public static List<IMethodBinding> methods(Collection<ITypeBinding> types) {
		List<IMethodBinding> ret = new ArrayList<IMethodBinding>();
		for (ITypeBinding ib : types) {
			ret.addAll(Arrays.asList(ib.getDeclaredMethods()));
		}

		return ret;
	}

	/** Direct bases (superclass and interfaces) of a type */
	public static List<ITypeBinding> bases(ITypeBinding tb, ITypeBinding object) {
		List<ITypeBinding> ret = new ArrayList<ITypeBinding>();

		TransformUtil.addDep(tb.getSuperclass(), ret);

		for (ITypeBinding ib : tb.getInterfaces()) {
			TransformUtil.addDep(ib, ret);
		}

		if (ret.isEmpty() && !TransformUtil.same(tb, Object.class)) {
			TransformUtil.addDep(object, ret);
		}

		return ret;
	}

	/** Recursive bases (superclasses and interfaces) of a type */
	public static List<ITypeBinding> allBases(ITypeBinding tb,
			ITypeBinding object) {
		List<ITypeBinding> ret = new ArrayList<ITypeBinding>();

		TransformUtil.addDep(tb.getSuperclass(), ret);
		if (tb.getSuperclass() != null) {
			for (ITypeBinding a : allBases(tb.getSuperclass(), object)) {
				TransformUtil.addDep(a, ret);
			}
		} else {
			TransformUtil.addDep(object, ret);
		}

		for (ITypeBinding ib : tb.getInterfaces()) {
			TransformUtil.addDep(ib, ret);

			for (ITypeBinding a : allBases(ib, object)) {
				TransformUtil.addDep(a, ret);
			}
		}

		return ret;
	}

	public static ITypeBinding objectBinding(ITypeBinding tb) {
		assert (TransformUtil.same(tb.createArrayType(1).getSuperclass(),
				Object.class));
		return tb.createArrayType(1).getSuperclass();
	}

	public static List<ITypeBinding> getBases(AST ast, ITypeBinding tb) {
		return bases(tb, ast.resolveWellKnownType(Object.class.getName()));
	}
}
