package se.arnetheduck.j2c.test.nesting;

public class LocalPrivateCtor {
	public static class A {
		private A() {
		}

		public A(int m) {
			this();
		}
	}

	A m() {
		return new A(5) {

		};
	}
}
