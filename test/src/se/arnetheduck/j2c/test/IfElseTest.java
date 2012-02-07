package se.arnetheduck.j2c.test;

public class IfElseTest {
	int m(int x) {
		if (x < 0) {
			return -1;
		} else if (x == 0) {
			return 0;
		} else {
			return 1;
		}
	}

	int m2(int x) {
		if (x < 0)
			return -1;
		else if (x == 0)
			return 0;
		else
			return 1;
	}

	int m3(int x) {
		if (x < 0) {
			return -1;
		} else if (x == 0) {
			return 0;
		}
		return 1;
	}

	int m4(int x) {
		if (x < 0)
			return -1;
		else if (x == 0)
			return 0;

		return 1;
	}
}
