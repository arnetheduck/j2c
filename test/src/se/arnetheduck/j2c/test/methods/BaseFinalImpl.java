package se.arnetheduck.j2c.test.methods;

public class BaseFinalImpl {
	public interface I {
		void m();

		void n();
	}

	public interface J {
		void m();
	}

	public static class S implements J {
		@Override
		public final void m() {
		}
	}

	public abstract static class T extends S {
	}

	public abstract static class U extends T implements I {
		@Override
		public abstract void n();
	}

	public static class V extends U {
		@Override
		public void n() {
		}
	}

	void m() {
		new S().m();
		new V().m();
	}
}
