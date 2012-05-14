package se.arnetheduck.j2c.test.switchx;

import se.arnetheduck.j2c.test.Empty;

public class SwitchTest {
	public int simple(int i) {
		switch (i) {
		case 1:
		case 2:
			return 0;
		case 3:
			i = i * 2;
			break;
		case 4: {
			i *= 4;
		}
		case 5:
		default:
			i = 4;
		}
		return i;
	}

	public int vars(int i) {
		switch (i) {
		case 1:
			int x = i;
			i = x * 2;
		case 2:
			x = 5;
			i = x;
			break;
		}

		return i;
	}

	public int multi(int i) {
		switch (i) {
		case 1: {
			Empty[] x, y[] = null;
			x = y[0];
			return i;
		}
		}
		return 0;
	}
}
