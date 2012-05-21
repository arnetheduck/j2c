package se.arnetheduck.j2c.test.methods;

public class GenericBaseImplements {
	public interface X<T> {
	}

	public interface A<T> {
		void m();
	}

	public interface B<T> extends A<T> {
		@Override
		void m();
	}

	public static class S<T> implements B<T> {
		@Override
		public void m() {
		}
	}

	<T> A<T> m() {
		return new S<T>();
	}
}
