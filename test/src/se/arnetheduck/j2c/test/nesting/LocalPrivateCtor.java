package se.arnetheduck.j2c.test.nesting;

public class LocalPrivateCtor {
	public static class A {
		private A() {
		}

		public A(int m) {
			this();
		}

		private A(int m, int n) {
		}
	}

	public static class B extends A {
		// Here, the private constructors should be accessible
		B() {
			super();
		}

		B(int m, int n) {
			super(m, n);
		}
	}

	A m() {
		return new A(5) {
		};
	}
}
