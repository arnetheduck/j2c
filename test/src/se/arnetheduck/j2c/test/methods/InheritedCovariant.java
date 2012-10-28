package se.arnetheduck.j2c.test.methods;

public class InheritedCovariant {

	public interface I {
	}

	public class A {
		I m() {
			return null;
		}
	}

	public class B extends A implements I {
		@Override
		C m() {
			return null;
		}
	}

	public class C extends B {
	}
}
