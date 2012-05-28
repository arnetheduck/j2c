package se.arnetheduck.j2c.test.nesting;

public class NewPrimary {
	public class A {
	}

	static A m(NewPrimary np) {
		return np.new A(); // Should become new A(np), not new A(this)
	}
}
