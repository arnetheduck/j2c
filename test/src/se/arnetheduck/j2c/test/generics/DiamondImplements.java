package se.arnetheduck.j2c.test.generics;

public class DiamondImplements {
	public interface I<T> {
		T m();
	}

	public interface J<T> extends I<T> {
	}

	public interface K<T> extends I<T> {
		@Override
		T m();
	}

	public class A<T> implements J<T> {
		@Override
		public T m() {
			return null;
		}
	}

	public class B extends A<String> implements K<String>, J<String> {
	}

	<T> I<T> m() {
		new B();
		return new A<T>();
	}

}
