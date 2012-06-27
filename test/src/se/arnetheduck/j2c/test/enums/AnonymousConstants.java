package se.arnetheduck.j2c.test.enums;

public enum AnonymousConstants {
	RED {
		@Override
		public int i() {
			return 0;
		}
	},

	GREEN {
		@Override
		public int i() {
			return 1;
		}
	},

	BLUE {
		@Override
		public int i() {
			return 2;
		}
	};

	public abstract int i();

	public AnonymousConstants c;
}
