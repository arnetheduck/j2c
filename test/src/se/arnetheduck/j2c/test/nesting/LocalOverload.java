package se.arnetheduck.j2c.test.nesting;

public class LocalOverload {

	void m() {
		class C {
			void n() {
			}
		}
		new C().n();
	}

	void m(int x) {
		class C {
			void o() {
			}
		}
		new C().o();
	}
}
