package se.arnetheduck.j2c.test;

public class ShiftTest {
	public int left() {
		return 1 << 5;
	}

	public int right() {
		return 1 >> 5;
	}

	public int rightUnsigned() {
		return -1 >>> 5;
	}

	public int rightUnsignedAssign() {
		int i = -5;
		i >>>= 1;
		return i;
	}
}
