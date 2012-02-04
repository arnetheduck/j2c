package se.arnetheduck.j2c.test;

public class StaticInit {
	static int x;
	static {
		x = 5;
	}

	static int y;

	static {
		y = 6;
	}

}
