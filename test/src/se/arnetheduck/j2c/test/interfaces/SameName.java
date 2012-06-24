package se.arnetheduck.j2c.test.interfaces;

public class SameName {
	public interface I {
		void m();
	}

	public interface J {
		void m();
	}

	public interface K extends I, J {

	}

	public class S implements I, J {
		@Override
		public void m() {
		}
	}

	public abstract class T implements K {
	}

	void m(I i, J j, K k, S s, T t) {
		i.m();
		j.m();
		k.m();
		s.m();
		t.m();
	}
}
