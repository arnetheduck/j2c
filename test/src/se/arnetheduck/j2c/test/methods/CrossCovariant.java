package se.arnetheduck.j2c.test.methods;

public class CrossCovariant {
	public interface I {
	}

	public static class A {
		I m() {
			return null;
		}
	}

	public static class B extends A implements I {
		@Override
		C m() {
			return null;
		}
	}

	public static class C extends A implements I {
		@Override
		B m() {
			return null;
		}
	}
}
