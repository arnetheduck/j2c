package se.arnetheduck.j2c.test.enums;

public enum AnonymousConstants {
	RED {
		@Override
		int i() {
			return 0;
		}
	},

	GREEN {
		@Override
		int i() {
			return 1;
		}
	},

	BLUE {
		@Override
		int i() {
			return 2;
		}
	};

	abstract int i();
}
