package se.arnetheduck.j2c.test.init;

public class Instance {
	static class Field {
		int x = 5;
	}

	static class Block {
		int x;
		{
			x = 5;
		}
	}
}
