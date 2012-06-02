package se.arnetheduck.j2c.test.nesting;

public class Subclass {
	int x;

	class A {

	}

	class C {
		int y;

		class B extends A {
			int m() {
				return x + y;
			}
		}

		class D extends B {

		}
	}
}
