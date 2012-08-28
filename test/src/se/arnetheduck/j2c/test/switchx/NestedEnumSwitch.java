package se.arnetheduck.j2c.test.switchx;

import se.arnetheduck.j2c.test.enums.SimpleEnum;

public class NestedEnumSwitch {
	protected int m(SimpleEnum e, SimpleEnum e2, SimpleEnum e3) {
		switch (e) {
		case BLUE:
			break;
		case RED:
			switch (e2) {
			case RED:
				switch (e3) {
				}
			}
		}

		switch (e) {
		case BLUE:
			break;
		case RED:
			switch (e2) {
			case RED:
				switch (e3) {
				}
			}
		}
		return 5;
	}
}