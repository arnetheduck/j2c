package se.arnetheduck.j2c.test.generics;

import se.arnetheduck.j2c.test.Empty;

public class ReturnCovariance {
	// Generic declaration
	interface I<A> {
		A m();
	}

	// Simple declaration
	interface J {
		Empty m();
	}

	public class SI<A> implements I<A> {
		@Override
		public A m() {
			return null;
		}
	}

	public class SJ<A extends Empty> implements J {
		@Override
		public A m() {
			return null;
		}
	}

	public class TI implements I<Empty> {
		@Override
		public Empty m() {
			return null;
		}
	}

	public class TJ implements J {
		@Override
		public Empty m() {
			return null;
		}
	}

	// Make sure all variants that need it get a dependency on Empty
	public class USIJ<T extends Empty> extends SI<T> implements J {
	}

	public class USJJ<T extends Empty> extends SJ<T> implements J {
	}

	public class UTIJ extends TI implements J {
	}

	public class UTJJ extends TJ implements J {
	}

	public class USII<T extends Empty> extends SI<T> implements I<T> {
	}

	public class USJI<T extends Empty> extends SJ<T> implements I<T> {
	}

	public class UTII extends TI implements I<Empty> {
	}

	public class UTJI extends TJ implements I<Empty> {
	}
}
