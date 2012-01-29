package se.arnetheduck.j2c.test;

public class StringTest {
	public String field;

	public String fieldInit = "Test";

	public static String statField;

	public static String statInitField = "Test";

	public static String[] array;

	public static String[] arrayInit = { "a", "b", "c" };

	public static String[] newArrayInit = new String[] { "a", "b", "c" };

	public String ret() {
		return "Test";
	}

	public String join() {
		return "Test" + "Test2";
	}

	public String multiJoin() {
		return join() + "Test3" + "Test4";
	}

	public String assign() {
		String str1 = "Test";
		str1 += "Test2";
		return str1;
	}

	public String primitives() {
		return "" + 1 + 1.2 + 'c';
	}

	public String objects() {
		return "" + this;
	}

}
