package se.arnetheduck.j2c.test.generics;

import se.arnetheduck.j2c.test.Empty;

public class MethodInvocationCast {
	public interface I<X> {
		X m();

		void m2(X x);
	}

	public interface B {
	}

	B m(I<? extends B> i) {
		return i.m();
	}

	void m2(I<Empty> x) {
		x.m2(null);
	}
}
