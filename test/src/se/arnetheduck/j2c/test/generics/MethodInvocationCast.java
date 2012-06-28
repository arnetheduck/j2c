package se.arnetheduck.j2c.test.generics;

import se.arnetheduck.j2c.test.Empty;
import se.arnetheduck.j2c.test.Method;
import se.arnetheduck.j2c.test.StringTest;

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

	void m2(I<Empty> x, Fields.A<Method> a) {
		a.t.m();
		x.m2(null);
	}

	private static final I<StringTest> canonicalTypes = null;

	static {
		canonicalTypes.m2(new StringTest());
	}

}
