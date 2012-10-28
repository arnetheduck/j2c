package se.arnetheduck.j2c.test.methods;

public class UnrelatedCovariant {
	public interface I {
		Object m();
	}

	public class A implements I {
		@Override
		public A m() {
			return null;
		}
	}

	public class B implements I {
		// A has a covariant return that shouldn't affect this covariance
		@Override
		public A m() {
			return null;
		}
	}
}
