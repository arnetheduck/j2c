package se.arnetheduck.j2c.test;

public class BoxTest {
	int k;

	void m() {
		Integer i = 42;
		int j = i;
		j = Integer.valueOf(j);
		i = Integer.bitCount(i);
		j = m2(i);
		i = m2(j);
	}

	int m2(Integer i) {
		return i;
	}

	static void x() {
		BoxTest bt = new BoxTest();
		Integer i = bt.k;
	}

	public java.lang.Object m0() {
		return (k);
	}
}
