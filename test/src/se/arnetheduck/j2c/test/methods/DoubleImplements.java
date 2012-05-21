package se.arnetheduck.j2c.test.methods;

public class DoubleImplements {
	public interface I {
		int m();
	}

	public interface J {
		int m();
	}

	public class A {
		public int m() {
			return 0;
		}
	}

	public class B extends A implements I, J {

	}

	I m() {
		new A();
		return new B();
	}
}
