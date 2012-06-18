package se.arnetheduck.j2c.test.generics;

public class BaseCall {
	public interface I<T> extends Implement.I<T> {
		@Override
		void m(T t);

		@Override
		T m2();
	}

	public static class X<T> extends Implement.S<T> implements I<T> {
	}
}
