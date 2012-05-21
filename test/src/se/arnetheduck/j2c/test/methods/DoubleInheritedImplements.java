package se.arnetheduck.j2c.test.methods;

public class DoubleInheritedImplements {
	public interface I {
		int m();
	}

	public interface J extends I {
		@Override
		int m();
	}

	public static class A implements I {
		@Override
		public int m() {
			return 0;
		}
	}

	public static class B extends A implements J {

	}

	I m() {
		new B();
		return new A();
	}
}
