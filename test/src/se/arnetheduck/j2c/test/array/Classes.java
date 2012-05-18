package se.arnetheduck.j2c.test.array;

import se.arnetheduck.j2c.test.Empty;
import se.arnetheduck.j2c.test.Sub;

public class Classes {
	Empty[] h;

	Sub[] h2;

	Object[] x() {
		Empty[] ee = h2;
		return ee;
	}

	Empty[] hclone() {
		return h.clone();
	}
}
