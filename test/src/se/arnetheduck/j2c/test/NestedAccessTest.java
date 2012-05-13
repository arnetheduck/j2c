package se.arnetheduck.j2c.test;

public class NestedAccessTest {
	static final int x = NestedTest.Static.Y;
	static final int z = NestedTest.Static.s();

	int m() {
		return NestedTest.Static.Y;
	}
}
