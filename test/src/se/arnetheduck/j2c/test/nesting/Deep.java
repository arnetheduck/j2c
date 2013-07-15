package se.arnetheduck.j2c.test.nesting;

public class Deep {
	void m() {
		class A {
			void m2() {
				class B {
					class C {
					}
				}
			}
		}
	}

	interface Y {
	}

	class X implements Y {
	}

	class A {
		class B extends X {
			class C {
				public Y get() {
					return B.this;
				}
			}
		}
	}
}
