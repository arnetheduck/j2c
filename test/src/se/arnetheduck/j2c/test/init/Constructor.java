package se.arnetheduck.j2c.test.init;

public class Constructor {
	static class Field {
		int x;

		Field() {
			x = 5;
		}
	}

	static class Delegated {
		int x;

		Delegated() {
			this(5);
		}

		Delegated(int x) {
			this.x = x;
		}
	}

	static class Inherited extends Delegated {
		Inherited() {
			super();
		}

		Inherited(int x) {
			super(5);
		}
	}
}
