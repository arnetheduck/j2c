package se.arnetheduck.j2c.test.array;

import se.arnetheduck.j2c.test.IEmpty;
import se.arnetheduck.j2c.test.ISub;

public class Interfaces {
	IEmpty[] h;

	ISub[] h2;

	Object[] x() {
		IEmpty[] ee = h2;
		return ee;
	}

	IEmpty[] hclone() {
		return h.clone();
	}
}
