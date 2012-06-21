package se.arnetheduck.j2c.test.generics;

import se.arnetheduck.j2c.test.Empty;

public class Fields {
	public static class A<T> {
		T t;
	}

	public static class B extends A<Empty> {
		Empty m() {
			t = new Empty();
			return t;
		}

		Empty n() {
			this.t = new Empty();
			return this.t;
		}
	}

	Empty m(A<Empty> a) {
		return a.t;
	}
}
