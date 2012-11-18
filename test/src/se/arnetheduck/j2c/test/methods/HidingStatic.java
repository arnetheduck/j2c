package se.arnetheduck.j2c.test.methods;

public class HidingStatic {
	public static class A {
		public static void m() {
		}
	}

	public static class B extends A {
		public static void m(int x) {
		}
	}

	void m() {
		// In C++, m must be accessed through A as the method overload is
		// shadowed by B::m
		B.m();
	}
}
