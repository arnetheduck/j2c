package se.arnetheduck.j2c.test.nesting;

import se.arnetheduck.j2c.test.Empty;

public class LocalNamed {
	Empty m() {
		class S extends Empty {
		}

		return new S();
	}
}
