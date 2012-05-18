package se.arnetheduck.j2c.test.array;

import se.arnetheduck.j2c.test.IEmpty;
import se.arnetheduck.j2c.test.ISub;

public class Mixed {
	public static class A implements IEmpty {

	}

	public static class B extends A implements ISub {

	}

	A[] m(B[] a) {
		return a;
	}
}
