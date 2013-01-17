package se.arnetheduck.j2c.test;

public class Volatile {
	volatile int x;
	volatile Volatile v;
	static volatile Volatile sv;
	static volatile int sx;

	int m() {
		x = x++;
		x = ++x;
		v.x++;
		java.lang.Object o = v;
		return o.hashCode() + v.hashCode() + x + v.v.hashCode();
	}

	void m2() {
		if (!(v instanceof V2)) {
			v = v;
		}
	}

	static int sm() {
		sx = sx++;
		sx = ++sx;
		sv.sx++;
		sv.x++;
		java.lang.Object o = sv;
		return o.hashCode() + sv.hashCode() + sx + sv.x;
	}

	static void sm2() {
		if (!(sv instanceof V2)) {
			sv = sv;
		}
	}

	public static class V2 extends Volatile {
	}
}
