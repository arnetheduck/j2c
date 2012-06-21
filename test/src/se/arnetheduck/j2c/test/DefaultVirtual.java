package se.arnetheduck.j2c.test;

public class DefaultVirtual {
	// Check that we don't override default access methods in another namespace
	NestedTest.Static m() {
		return null;
	}

	void n() {
	}
}
