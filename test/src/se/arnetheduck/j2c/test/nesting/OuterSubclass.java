package se.arnetheduck.j2c.test.nesting;

public class OuterSubclass {
	int y;

	class A {
		int x;

		class B {
		}
	}

	class C extends A {
		class D extends B {
			int m() {
				return x + y;
			}
		}
	}
}
