package se.arnetheduck.j2c.test.nesting;

public class ClosureInInitializer {
	public static Object m(final int x) {
		return new Object() {
			private final Object action = new Object() {
				public void run() {
					if (x == 0) {
					}
				}
			};
		};
	}
}
