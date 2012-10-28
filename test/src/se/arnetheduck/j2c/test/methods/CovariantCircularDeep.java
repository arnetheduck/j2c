package se.arnetheduck.j2c.test.methods;

public class CovariantCircularDeep {
	public static interface I {
	}

	public static abstract class A {
		public abstract I m();
	}

	public static class B extends A implements I {
		@Override
		public C m() {
			return null;
		} // Needs C
	}

	public static class C extends A implements I {
		@Override
		public B m() { // Needs B - circular!
			return null;
		}
	}
}
