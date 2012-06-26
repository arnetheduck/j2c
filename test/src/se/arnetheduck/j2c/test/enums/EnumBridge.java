package se.arnetheduck.j2c.test.enums;

import java.util.Comparator;

import se.arnetheduck.j2c.test.Empty;

public enum EnumBridge implements Comparator<Empty> {
	FORWARD {
		@Override
		public int compare(Empty f1, Empty f2) {
			return 0;
		}
	}
};
