package se.arnetheduck.j2c.test.interfaces;

public class SameNameOverload {
	public interface I {
		void m();
	}

	public interface J {
		void m(int i);
	}

	public interface K extends I, J {

	}

	public class S implements I, J {
		@Override
		public void m() {
		}

		@Override
		public void m(int i) {
		}
	}

	public abstract class T implements K {
	}

	void m(I i, J j, K k, S s, T t) {
		i.m();
		j.m(1);
		k.m();
		k.m(1);
		s.m();
		s.m(1);
		t.m();
		t.m(1);
	}
}
