package se.arnetheduck.j2c.test.methods;

import static se.arnetheduck.j2c.test.enums.AnonymousConstants.BLUE;

public class StaticInvokes {
	static class A {
		static A s() {
			return null;
		}

		A m() {
			return null;
		}
	}

	void m(A a) {
		A.s(); // Static invoke
		a.s(); // Static invoke through instance
		A.s().s(); // Static invoke through returned instance

		a.m(); // Invoke through instance
		a.m().m(); // Invoke through returned instance

		BLUE.c.i();
	}
}
