package se.arnetheduck.j2c.test.methods;

public class ImplementOverloads {
	public interface A {
		void m(int i);
	}

	public class S implements A {
		@Override
		public void m(int i) {
		}

		public void m(int i, int j) {
		}
	}

	public class T extends S implements A {
		void x() {
			m(1, 2);
		}
	}
}
