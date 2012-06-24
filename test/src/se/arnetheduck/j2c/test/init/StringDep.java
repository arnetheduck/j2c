package se.arnetheduck.j2c.test.init;

public class StringDep {
	// Check that we get correct includes even if we make no strings
	static final String x = ConstExpr.ss + ConstExpr.ss;
}
