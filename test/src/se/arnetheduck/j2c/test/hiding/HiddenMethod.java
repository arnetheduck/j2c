package se.arnetheduck.j2c.test.hiding;

public class HiddenMethod {
	public class A {
		int a;
	}

	public class B extends A {
		int a;
	}

	public class C extends A {
		int a() {
			return a;
		}
	}

	int m(A a, B b, C c) {
		return a.a + b.a + c.a + c.a();
	}
}
