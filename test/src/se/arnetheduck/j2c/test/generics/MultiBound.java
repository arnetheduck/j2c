package se.arnetheduck.j2c.test.generics;

public class MultiBound {
	interface A {
		void a();
	}

	interface B {
		void b();
	}

	<T extends A & B> void m(T t) {
		A a = t;
		B b = t;
		t.a();
		t.b();
		a(t);
		b(t);
	}

	void a(A a) {
	}

	void b(B b) {
	}
}
