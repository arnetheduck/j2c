package se.arnetheduck.j2c.test.switchx;

public class Empty {
	public void empty(int x) {
		switch (x) {
		}
	}

	public void emptyDefault(int x) {
		switch (x) {
		default:
		}
	}

	public void emptyCase(int x) {
		switch (x) {
		case 1:
		}
	}
}
