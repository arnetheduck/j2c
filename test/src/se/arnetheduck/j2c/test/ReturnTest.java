package se.arnetheduck.j2c.test;

/** Return covariance tests */
public class ReturnTest {
	@Override
	protected Void clone() throws CloneNotSupportedException {
		return null;
	}

	public static class A {
		public Object a() {
			return null;
		}
	}

	public static class B extends A {

	}

	public static class C extends B {
		@Override
		public B a() {
			return null;
		}
	}

	public B c(C c) {
		return c.a();
	}
}
