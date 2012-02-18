package se.arnetheduck.j2c.test;

public class NestedTest {
	private static int x;

	private static int a() {
		return 5;
	}

	public static class Static {
		int m() {
			return x;
		}
	}

	public static class SubStatic extends Static {
		int x() {
			return m() + a();
		}
	}

	public class Inner {
		int m() {
			return x;
		}
	}

	public class SubInner extends Inner {
		int x() {
			return m() + a();
		}
	}

	public void m() {
		class Local {
			int m2() {
				return x + a();
			}
		}

		Object o = new Object() {
			int m3() {
				return x + a();
			}

			int m4() {
				Object o3 = new Object() {
					public int m4() {
						return x;
					}
				};

				return o3.hashCode();
			}
		};

		new Static();
		new Inner();
		new Local();
	}
}
