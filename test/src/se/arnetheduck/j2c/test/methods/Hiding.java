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
	}

	void m(A a, B b, C c, S s) {
		a.m();
		b.m();
		c.m();
		s.m();
		c.m(1);
		s.m(1);
		s.m(1, 2);
	}
}