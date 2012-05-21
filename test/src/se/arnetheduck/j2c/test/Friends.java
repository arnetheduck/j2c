package se.arnetheduck.j2c.test;

public class Friends {
	private int a;

	private static class A {
		private static class C {
			private static final int e = 0;
		}

		private static final int b = 0;
		private int c;
	}

	private class B extends A {
		public int m(Object o) {
			new A() {
				int m() {
					return A.C.e;
				}
			};
			return a + ((A) o).c + A.b + A.C.e; // Access to private vars
		}
	}
}
