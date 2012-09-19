package se.arnetheduck.j2c.test.switchx;

public class InterfaceValues {
	public interface I {
		int X = 1;
		int Y = 2;
	}

	public class S implements I {
		void m(int i) {
			switch (i) {
			case X: // check that we can reference field in interface correctly
				return;
			}
		}
	}

}
