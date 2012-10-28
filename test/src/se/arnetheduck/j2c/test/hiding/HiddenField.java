package se.arnetheduck.j2c.test.hiding;

public class HiddenField {
	public class A {
		int a() {
			return 0;
		}
	}

	public class B extends A {
		@Override
		int a() {
			return 0;
		}
	}

	public class C extends A {
		int a;
	}

	int m(A a, B b, C c) {
		return a.a() + b.a() + c.a() + c.a;
	}
}
