package se.arnetheduck.j2c.test;

public class VarargTest {
	public void m(int a, int... b) {

	}

	public void m2() {
		m(1);
		m(1, 2);
		m(1, 2, 3);
		m(1, 2, 3, 4);
	}

	public void m3() {
		m(1, new int[] { 1, 2 });
	}

	public static class VA<T> {

	}

	public void m4(VA<?>... x) {
	}

	public void m5() {
		VA[] x = new VA[0];
		m4(x);
	}

	public void m6(Object... x) {
		m6(4, "rte");
	}
}
