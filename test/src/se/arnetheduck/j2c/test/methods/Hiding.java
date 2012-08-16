package se.arnetheduck.j2c.test.methods;

public class Hiding {
	public interface A {
		int m();
	}

	public interface B extends A {

	}

	public interface C extends B {
		int m(int a);
	}

	public abstract class S implements C {
		@Override
		public abstract int m(int a);

		public int m(int a, int b) {
			return a + b;
		}

		private int m(int a, int b, int c) {
			return m(a, b) + c;
		}
	}

	public class T extends S {
		@Override
		public int m() {
			return 0;
		}

		@Override
		public int m(int a) {
			return 0;
		}
	}

	public class U extends Implement.S {
		void m(int x) {
		}
	}

	void m(A a, B b, C c, S s, T t, U u) {
		a.m();
		b.m();
		c.m();
		s.m();
		c.m(1);
		s.m(1);
		s.m(1, 2);
		t.m();
		t.m(1);
		u.m(null);
		u.m(1);
	}
}