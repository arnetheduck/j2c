package se.arnetheduck.j2c.test.nesting;

public class DeepSiblings {
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

		Object m() {
			return new Object() {
				A x() {
					return new A(a);
				}
			};
		}
	}

}
