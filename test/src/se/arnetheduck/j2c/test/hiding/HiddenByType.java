package se.arnetheduck.j2c.test.hiding;

public class HiddenByType {
	public class C implements B {
		int m() {
			return B;
		}
	}
}
