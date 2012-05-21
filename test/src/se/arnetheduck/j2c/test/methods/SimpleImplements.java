package se.arnetheduck.j2c.test.methods;

public class SimpleImplements {
	public interface I {
		void b(byte x);
	}

	public static class A {
		public void b(byte x) {
		}
	}

	public static class B extends A implements I {
	}

	I m() {
		return new B();
	}
}
