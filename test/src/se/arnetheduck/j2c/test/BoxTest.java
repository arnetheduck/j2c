package se.arnetheduck.j2c.test;

public class BoxTest {
	int k;

	void m() {
		Integer i = 42;
		int j = i;
	}

	static void x() {
		BoxTest bt = new BoxTest();
		Integer i = bt.k;
	}
}
