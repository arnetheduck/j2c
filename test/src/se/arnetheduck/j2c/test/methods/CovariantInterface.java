package se.arnetheduck.j2c.test.methods;

import se.arnetheduck.j2c.test.Empty;

public class CovariantInterface {
	public interface I {
		Object m();
	}

	public interface J extends I {
		@Override
		Empty m();
	}

	public static class S implements I {
		@Override
		public Object m() {
			return null;
		}
	}

	public static class T extends S implements J {
		@Override
		public Empty m() {
			return null;
		}
	}

	void m() {
		S s = new S();
		T t = new T();
		I i = s;
		J j = t;

		Object o = s.m();
		Empty e = t.m();
		o = i.m();
		e = j.m();
	}
}
