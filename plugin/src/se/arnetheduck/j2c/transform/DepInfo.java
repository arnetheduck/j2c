package se.arnetheduck.j2c.transform;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;

import org.eclipse.jdt.core.dom.ITypeBinding;

public class DepInfo {
	private final Transformer ctx;

	private final Set<ITypeBinding> hardDeps = new TreeSet<ITypeBinding>(
			new BindingComparator());
	private final Set<ITypeBinding> softDeps = new TreeSet<ITypeBinding>(
			new BindingComparator());

	private boolean javaCast;
	private boolean npc;
	private boolean atomic;

	public DepInfo(Transformer ctx) {
		this.ctx = ctx;
	}

	/** Soft dependency, forward declaration sufficient */
	public void soft(ITypeBinding dep) {
		if (TransformUtil.addDep(dep, softDeps)) {
			ctx.softDep(dep);
		}

		checkArray(dep, false);
	}

	/** Hard dependency, class declaration required */
	public void hard(ITypeBinding dep) {
		if (TransformUtil.addDep(dep, hardDeps)) {
			ctx.hardDep(dep);
			soft(dep);
		}

		checkArray(dep, true);
	}

	private void checkArray(ITypeBinding dep, boolean hard) {
		if (!dep.isArray())
			return;

		ITypeBinding ct = dep.getComponentType();
		if (ct.isPrimitive())
			return;

		if (TransformUtil.same(ct, Object.class))
			return;

		if (hard) {
			hard(ct);
		} else {
			soft(ct);
		}

		List<ITypeBinding> bases = arrayBases(ct);
		for (ITypeBinding base : bases) {
			if (hard) {
				hard(base);
			} else {
				soft(base);
			}
		}

		if (hard) {
			hard(ctx.resolve(ArrayStoreException.class));
		}
	}

	public Set<ITypeBinding> getHardDeps() {
		return hardDeps;
	}

	public Set<ITypeBinding> getSoftDeps() {
		return softDeps;
	}

	public boolean needsJavaCast() {
		return javaCast;
	}

	public void setJavaCast() {
		javaCast = true;
		hard(ctx.resolve(ClassCastException.class));
	}

	public boolean needsNpc() {
		return npc;
	}

	public void setNpc() {
		npc = true;
		hard(ctx.resolve(NullPointerException.class));
	}

	public boolean needsAtomic() {
		return atomic;
	}

	public void setNeedsAtomic() {
		atomic = true;
	}

	public void printArrays(PrintWriter out) {
		Set<ITypeBinding> done = new TreeSet<ITypeBinding>(
				new BindingComparator());

		boolean printed = false;
		Stack<String> cur = new Stack<String>();
		for (ITypeBinding dep : softDeps) {
			printed |= printArray(out, dep, done, cur);
		}

		TransformUtil.setNs(out, new ArrayList<String>(), cur);
		if (printed) {
			out.println();
		}
	}

	private boolean printArray(PrintWriter out, ITypeBinding dep,
			Set<ITypeBinding> done, Stack<String> cur) {
		dep = dep.getErasure();

		if (done.contains(dep))
			return false;

		if (!dep.isArray())
			return false;

		ITypeBinding ct = dep.getComponentType();
		if (ct.isPrimitive() || TransformUtil.same(ct, Object.class))
			return false;

		printArray(out, ct, done, cur);

		List<ITypeBinding> bases = arrayBases(ct);
		for (ITypeBinding base : bases) {
			printArray(out, base, done, cur);
		}

		if (done.isEmpty()) {
			out.print("template<typename ComponentType, typename... Bases> struct SubArray;");
		}

		TransformUtil.setNs(out,
				Arrays.asList(CName.packageOf(ct).split("\\.")), cur);

		out.print("typedef ::SubArray< " + CName.qualified(ct, true));

		for (ITypeBinding base : bases) {
			out.print(", " + CName.relative(base, ct, true));
		}

		out.println(" > " + CName.of(dep) + ";");

		done.add(dep);

		return true;
	}

	private List<ITypeBinding> arrayBases(ITypeBinding ct) {
		List<ITypeBinding> ret = new ArrayList<ITypeBinding>();
		if (ct.isArray()) {
			ret.add(ctx.resolve(Cloneable.class).createArrayType(1));
			ret.add(ctx.resolve(Serializable.class).createArrayType(1));
			return ret;
		}

		if (ct.getSuperclass() == null) {
			ret.add(ctx.resolve(Object.class).createArrayType(1));
		} else {
			ret.add(ct.getSuperclass().createArrayType(1));
		}

		for (ITypeBinding cti : ct.getInterfaces()) {
			ret.add(cti.createArrayType(1));
		}

		return ret;
	}
}
