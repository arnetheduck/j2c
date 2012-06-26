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
}
