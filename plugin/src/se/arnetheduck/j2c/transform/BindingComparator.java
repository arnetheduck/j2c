package se.arnetheduck.j2c.transform;

import java.util.Comparator;

import org.eclipse.jdt.core.dom.IBinding;

public final class BindingComparator implements Comparator<IBinding> {
	@Override
	public int compare(IBinding o1, IBinding o2) {
		return o1.getKey().compareTo(o2.getKey());
	}
}