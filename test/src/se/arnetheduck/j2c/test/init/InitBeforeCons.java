package se.arnetheduck.j2c.test.init;

public class InitBeforeCons {
	public static class Field {
		int x = 5;

		Field() {
			x = 6;
		}

		Field(int x) {
			this.x = x;
		}
	}

	static class Inherited extends Field {
		{
			x = 7;
		}

		Inherited() {
			super(8);
		}
	}
}
