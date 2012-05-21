package se.arnetheduck.j2c.test.methods;

public class DiamondImplements {
	public interface I {
		int m();
	}

	public interface J extends I {
	}

	public interface K extends I {
		@Override
		int m();
	}

	public class A implements J {
		@Override
		public int m() {
			return 0;
		}
	}

	public class B extends A implements K, J {
	}

	I m() {
		new B();
		return new A();
	}
}
