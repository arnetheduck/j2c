package se.arnetheduck.j2c.test.nesting;

public class ClosureNames {
	Object m() {
		final int x = 1;
		return new Object() {
			public int x() {
				return x; // same name as closed variable
			}
		};
	}

	public static class X {
		public X(int o) {
		}
	}

	public static X n(final Object o) {
		return new X(1) {
			public int m() {
				// closure and generated constructor parameter for local type
				// have same name (param gets name from X.X(int o))
				return o.hashCode();
			}
		};
	}
}
