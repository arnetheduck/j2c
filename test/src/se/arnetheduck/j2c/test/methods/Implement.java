package se.arnetheduck.j2c.test.methods;

public class Implement {
	public interface I {
		void m(Object t);

		Object m2();
	}

	public static class S implements I {
		@Override
		public void m(Object t) {
		}

		@Override
		public Object m2() {
			return null;
		}

		private void m() { // Private unhiding check (Hiding.java)
		}
	}
}
