package se.arnetheduck.j2c.test.methods;

public class Using {
	interface A {
		void m();
	}

	interface B extends A {
		void m(int a);
	}

	public static abstract class T implements A, B {
		public void m(int a, int b) {
			m(1);
		}

		@Override
		public abstract void m();
	}
}
