package se.arnetheduck.j2c.test.methods;

public class BaseImplements {
	public interface I {
		void m();

		void m(int a, int b);

		void n();
	}

	public interface J extends I {
	}

	public static class A {
		public void m() {
		}
	}

	public static abstract class B extends A implements I {
		// No need to implement I.m() here
		void m(int x) { // ...but this could shadow m() in C++
		}

		@Override
		public abstract void n();
	}

	public static class C implements I {
		@Override
		public void m() {
		}

		@Override
		public void n() {
		}

		@Override
		public void m(int a, int b) {
		}
	}

	public static class D extends C implements J {
		public void m(int i) {
		}
	}

	public I m() {
		return new D();
	}
}
