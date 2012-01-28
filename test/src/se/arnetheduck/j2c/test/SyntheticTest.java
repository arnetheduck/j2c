package se.arnetheduck.j2c.test;

public class SyntheticTest {
	public Integer testTypeBinding() {
		// Integer implements Comparable<T> which needs a compareTo bridge
		return new Integer(5);
	}

	public StringBuffer testCovariantReturn() {
		// StringBuffer has a covariant return type bridge that shouldn't show
		// up
		return new StringBuffer();
	}
}
