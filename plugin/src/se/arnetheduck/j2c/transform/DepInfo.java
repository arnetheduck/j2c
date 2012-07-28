package se.arnetheduck.j2c.transform;

import java.util.Set;
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

	public DepInfo(Transformer ctx) {
		this.ctx = ctx;
	}

	/** Soft dependency, forward declaration sufficient */
	public void soft(ITypeBinding dep) {
		if (TransformUtil.addDep(dep, softDeps)) {
			ctx.softDep(dep);
		}
	}

	/** Hard dependency, class declaration required */
	public void hard(ITypeBinding dep) {
		if (TransformUtil.addDep(dep, hardDeps)) {
			ctx.hardDep(dep);
			soft(dep);
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
}
