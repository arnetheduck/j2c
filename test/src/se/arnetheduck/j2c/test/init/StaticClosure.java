package se.arnetheduck.j2c.test.init;

public class StaticClosure {
	static {
		final int p = 5;
		new Object() {
			void run() {
				int i = p;
			}
		};
	}
}
