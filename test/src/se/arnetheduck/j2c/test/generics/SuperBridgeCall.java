package se.arnetheduck.j2c.test.generics;

import se.arnetheduck.j2c.test.Empty;

public class SuperBridgeCall {
	public interface I<X> {
		void m(X i);
	}

	public class S {
		public void m(Empty e) {
		}
	}

	public class T extends S implements I<Empty> {
	}

	public class U extends T implements I<Empty> {
	}

	T m(I<Empty> i) {
		i.m(null);

		return new T();
	}
}
