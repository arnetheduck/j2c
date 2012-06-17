package se.arnetheduck.j2c.test;

public class ConstructorTest {
	public int n;

	public ConstructorTest(int i) {
		n = i;
		new Empty();
	}

	public ConstructorTest(short s) {
		this((int) s);
	}

	public void ConstructorTest() {
		// This is not a constructor!
	}
}
