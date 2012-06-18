package se.arnetheduck.j2c.test.methods;

public class BaseCall {
	public interface I extends Implement.I {
		@Override
		void m(Object t);

		@Override
		Object m2();
	}

	public static class X extends Implement.S implements I {
	}
}
