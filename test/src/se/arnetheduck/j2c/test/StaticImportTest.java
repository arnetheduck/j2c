package se.arnetheduck.j2c.test;

import static java.lang.Math.sqrt;
import static java.lang.reflect.Modifier.ABSTRACT;

public class StaticImportTest {
	int x() {
		return (int) sqrt(ABSTRACT);
	}
}
