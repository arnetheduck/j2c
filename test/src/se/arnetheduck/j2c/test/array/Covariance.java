package se.arnetheduck.j2c.test.array;

import se.arnetheduck.j2c.test.Empty;

public class Covariance {
	public static abstract class Base {
		public abstract Empty[] m();
	}

	public static class Child extends Base {
		@Override
		public Empty[] m() {
			return null;
		}
	}
}
