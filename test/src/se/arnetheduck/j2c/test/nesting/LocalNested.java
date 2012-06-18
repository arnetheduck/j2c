package se.arnetheduck.j2c.test.nesting;

/** Nested type used as base for a local type */
public class LocalNested {
	public class A {
	}

	Object m() {
		return new A() {
		};
	}

}
