package se.arnetheduck.j2c.test.nesting;

public class StaticNonStatic {
	public static class A {
		int x;

		public class B {
			int m() {
				return x;
			}
		}
	}

	void m() {
		new A().new B().m();
	}
}
