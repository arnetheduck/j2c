package se.arnetheduck.j2c.test.nesting;

public class MethodParam {
	int a;

	public class A {
		void m(int x) {
		}

		void m2() {
			m(a);
		}

		void m3() {
			A b = new A();
			b.m(a);
		}
	}
}
