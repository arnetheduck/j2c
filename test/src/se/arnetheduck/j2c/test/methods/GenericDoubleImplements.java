package se.arnetheduck.j2c.test.methods;

public class GenericDoubleImplements {
	public interface I<T> {
		int m();
	}

	public interface J<T> {
		int m();
	}

	public class A {
		public int m() {
			return 0;
		}
	}

	public class B<T> extends A implements I<T>, J<T> {

	}

	<T> I<T> m() {
		new A();
		return new B<T>();
	}
}
