package se.arnetheduck.j2c.test;

public class ArrayTest {
	int[] a;
	int b[];

	Empty[] h;

	Sub[] h2;

	int[] c() {
		return null;
	}

	void d(int[] e) {
	}

	void f(int g[]) {
	}

	Object[] x() {
		Empty[] ee = h2;
		return ee;
	}

	int[] aclone() {
		return a.clone();
	}

	Empty[] hclone() {
		return h.clone();
	}
}
