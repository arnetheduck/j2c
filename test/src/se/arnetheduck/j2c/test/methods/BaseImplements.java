package se.arnetheduck.j2c.test.methods;

public class BaseImplements {
	public interface I {
		void m();
	}

	public static class A {
		public void m() {
		}
	}

	public static class B extends A implements I {
		// No need to implement I.m() here
		void m(int x) { // ...but this could shadow m() in C++
		}
	}

	public I m() {
		return new B();
	}
}
