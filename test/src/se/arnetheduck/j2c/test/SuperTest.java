package se.arnetheduck.j2c.test;

public class SuperTest extends ConstructorTest {
	public SuperTest() {
		this(6);
	}

	public SuperTest(int i) {
		super(i);
	}

	static class X extends Exception {
		X() {
			super("Test");
		}
	}
}
