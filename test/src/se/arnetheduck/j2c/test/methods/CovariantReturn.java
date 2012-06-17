package se.arnetheduck.j2c.test.methods;

import se.arnetheduck.j2c.test.DefaultVirtual;
import se.arnetheduck.j2c.test.NestedTest;

public class CovariantReturn {
	public static class Static extends NestedTest.Static {

	}

	public static class C {
		NestedTest.Static m() {
			return null;
		}
	}

	public static class D extends C {
		@Override
		Static m() {
			return null;
		}
	}

	public static class E extends DefaultVirtual {
		Static m() {
			return null;
		}
	}

	void m(C c, D d, E e) {
		c.m();
		d.m();
		e.m();
	}
}
