package se.arnetheduck.j2c.test;

public class NestedTest {
	public static class Static {

	}

	public class Inner {

	}

	public void m() {
		class Local {

		}

		Object o = new Object() {
			// Anonymous
		};

		new Static();
		new Inner();
		new Local();
	}
}
