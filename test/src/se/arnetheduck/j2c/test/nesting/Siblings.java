package se.arnetheduck.j2c.test.nesting;

public class Siblings {
	int a;

	class A {
		int x;

		A(int x) {
			this.x = x;
		}

		B m() {
			return new B(a);
		}
	}

	class B {
		int x;

		public B(int x) {
			this.x = x;
		}

		A m() {
			return new A(a);
		}
	}
}
