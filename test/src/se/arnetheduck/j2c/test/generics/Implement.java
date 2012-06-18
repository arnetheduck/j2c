package se.arnetheduck.j2c.test.generics;

public class Implement {
	public interface I<T> {
		void m(T t);

		T m2();
	}

	public static class S<T> implements I<T> {
		@Override
		public void m(T t) {
		}

		@Override
		public T m2() {
			return null;
		}
	}
}
