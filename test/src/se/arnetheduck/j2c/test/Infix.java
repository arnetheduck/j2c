package se.arnetheduck.j2c.test;

public class Infix {
	public interface I {

	}

	public interface J extends I {

	}

	public class S implements I {

	}

	boolean m(S s, J j) {
		// Comparison between distinct pointer types in C++ needs cast
		// to common base class
		return s == j;
	}

	boolean nullComp(Object o) {
		// This shouldn't require a cast
		return o == null;
	}

	boolean m(int i, char c) {
		return i == c;
	}
}
