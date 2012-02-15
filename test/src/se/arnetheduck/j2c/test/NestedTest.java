package se.arnetheduck.j2c.test;

public class NestedTest {
	private static int x;

	public static class Static {
		int m() {
			return x;
		}
	}

	public class Inner {
		int m() {
			return x;
		}
	}

	public void m() {
		class Local {
			int m2() {
				return x;
			}
		}

		Object o = new Object() {
			int m3() {
				return x;
			}
		};

		new Static();
		new Inner();
		new Local();
	}
}
