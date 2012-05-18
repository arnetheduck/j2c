package se.arnetheduck.j2c.test.array;

public class Primitives {
	int[] a;
	int b[];

	int[] c() {
		return null;
	}

	void d(int[] e) {
	}

	void f(int g[]) {
	}

	int[] aclone() {
		return a.clone();
	}

	int aclone2()[] {
		return a.clone();
	}
}
