package se.arnetheduck.j2c.test.nesting;

public class SameName {
	static int m() {
		return 42;
	}

	int n() {
		return 42;
	}

	public class S {
		void x() {
			int n = n();
		}
	}

	public static class T {
		void x() {
			int m = m();
		}
	}
}
