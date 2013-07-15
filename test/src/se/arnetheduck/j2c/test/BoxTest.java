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

		Short s = 42;
		Byte b = 33;

		java.lang.Object o = '\u0000';
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

	public java.lang.Object m1(java.lang.Object o) {
		return ((BoxTest) o).k;
	}

	static void genericBox() {
		gbox('c');
		gbox(0l);
		gbox(0);
		gbox(0.0f);
		gbox(0.0);
	}

	static <T> void gbox(T o) {
	}
}
