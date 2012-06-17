package se.arnetheduck.j2c.test.methods;

public class AmbiguousSuper {
	public interface I {
		void m();
	}

	public interface J extends I {
	}

	public class S {
		public void m() {
		}
	}

	public abstract class T extends S implements J {

	}

	public class U extends S implements J {

	}

	void m(I i, S s, T t, U u) {
		i.m();
		s.m();
		t.m();
		u.m();
	}
}
