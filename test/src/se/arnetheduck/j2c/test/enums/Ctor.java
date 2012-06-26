package se.arnetheduck.j2c.test.enums;

public class Ctor<T> {
	public static final boolean B = false;

	public static enum Y {
		X(false), Z(B);

		Y(boolean x) {
		}
	}
}
