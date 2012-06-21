package se.arnetheduck.j2c.test.methods;

import se.arnetheduck.j2c.test.DefaultVirtual;
import se.arnetheduck.j2c.test.NestedTest;

public class DefaultVirtualOverride {
	public static abstract class S extends DefaultVirtual {
		abstract NestedTest.Static m();
	}

	public static class T extends S {
		@Override
		NestedTest.Static m() {
			return null;
		}
	}

	void m() {
		new T().m();
	}
}
