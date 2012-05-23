package se.arnetheduck.j2c.test.init;

public class Static {
	static class Field {
		static int x = 5;
	}

	static class Block {
		static int x;
		static {
			x = 5;
		}
	}

}
