package se.arnetheduck.j2c.test;

public class TryTest {
	public int tryCatch() {
		int i;
		try {
			throw new Error();
		} catch (Throwable t) {
			i = 6;
		}

		return i;
	}

	public int tryFinally(int i) {
		try {
			if (i < 5)
				throw new Error();
		} finally {
			i = 6;
		}

		return i;
	}

	public int tryCatchFinally(int i) {
		try {
			if (i < 5)
				throw new Error();
		} catch (Throwable t) {
			i = 42;
		} finally {
			i = 6;
		}

		return i;
	}
}
