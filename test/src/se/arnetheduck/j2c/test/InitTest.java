package se.arnetheduck.j2c.test;

public class InitTest {
	static {
		java.lang.Object o = new ParamConstructor(3) {
		};
		java.lang.Object o2 = null;
		java.lang.Object o3 = o2, o4 = null;

		java.lang.Object x = "";
		x = (InitTest) o;
	}
}
