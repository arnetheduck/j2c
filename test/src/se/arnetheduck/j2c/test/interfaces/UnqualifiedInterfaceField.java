package se.arnetheduck.j2c.test.interfaces;

public class UnqualifiedInterfaceField {
	public interface I {
		int i = 42;
	}

	public class S implements I {
		int m() {
			return i;
		}
	}

	public class Hidden implements I {
		void m(int x) {
		}

		int i() {
			m(i);
			return i;
		}
	}
}
