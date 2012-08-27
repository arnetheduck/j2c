package se.arnetheduck.j2c.test;

public class BridgeTest {
	public interface I<T extends Empty> {
		void t(T x);
	}

	public interface J extends I<Empty> {
		@Override
		void t(Empty x);
	}

	public interface K extends I<Empty>, J {
		// Both unhiding and dupe base method (see dupeNames)
		void t(Object o);
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
