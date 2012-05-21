package se.arnetheduck.j2c.test.methods;

public class Overrides {
	public interface A {
		int add();
	}

	public interface B extends A {
		int add(int a);
	}

	public interface C extends B {
		int add(int a, int b);
	}

	int m(C c) {
		return c.add() + c.add(1) + c.add(1, 2);
	}

	public static class S {
		private void a() {
		}

		public void a(int a, int b) {
		}
	}

	public static class T extends S {
		public void a(int b) {
		}
	}

	public static class U extends S {
		private void a(int b) {
		}
	}

	void m(T t) {
		t.a(0);
		t.a(0, 4);
	}

	void m(U s) {
		s.a(0, 4);
	}
}
