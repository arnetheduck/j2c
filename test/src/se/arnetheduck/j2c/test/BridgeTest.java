package se.arnetheduck.j2c.test;

public class BridgeTest {
	public interface I<T extends Empty> {
		void t(T x);
	}

	public static class A extends Empty {

	}

	public static class B extends A {

	}

	public static class ImplA implements I<A> {
		@Override
		public void t(A x) {

		}
	}

	public static class ImplB implements I<B> {
		@Override
		public void t(B x) {

		}
	}

	public void m() {
		new ImplA();
		new ImplB();
	}
}
