package se.arnetheduck.j2c.test;

public class NestedAccessTest {
	static final int x = NestedTest.Static.Y;
	static final int z = NestedTest.Static.s();

	final int y = 5;

	static final NestedAccessTest NAT = new NestedAccessTest();

	int m() {
		return NestedTest.Static.Y;
	}

	static class T {
		static final int a = x;
		static final int b0 = NAT.m();
		static final int b1 = NAT.NAT.m();

		static final int c0 = NAT.y;
		static final int c1 = NAT.NAT.y;
	}
}
