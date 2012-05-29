package se.arnetheduck.j2c.test;

public class SuperTest extends ConstructorTest {
	public SuperTest() {
		this(6);
	}

	public SuperTest(int i) {
		super(i);
	}

	static class S {
	}

	static class T extends S {
		T() {
			super();
		}
	}

	static class Y {
		Y(String x) {
		}
	}

	static class X extends Y {
		X() {
			super("Test");
		}
	}
}
