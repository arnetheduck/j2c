package se.arnetheduck.j2c.test.switchx;

import se.arnetheduck.j2c.test.EnumTest;

public class SwitchEnum {
	public int m(EnumTest et) {
		switch (et) {
		case RED:
		case GREEN:
			return 0;
		case BLUE:
			break;
		default:
			return 5;

		}

		return 1;
	}
}
