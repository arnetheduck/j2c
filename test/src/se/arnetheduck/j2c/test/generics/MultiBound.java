package se.arnetheduck.j2c.test.generics;

public class MultiBound {
	interface A {
		void a();
	}

	interface B {
		void b();
	}

	<T extends A & B> void m(T t) {
		t.a();
		t.b();
	}
}
