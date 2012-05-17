package se.arnetheduck.j2c.test;

public class LiteralTest {
	void m() {
		char c0 = ' ';
		char c1 = '\u2233';
		char[] c2 = new char[] { ' ' };
		char c3 = '\uD800'; // These two are not valid as c++ utf-16 character
		char c4 = '\uDFFF'; // literals
		char c5 = '\u00f0';
		String s0 = " ";
		String s1 = "\u2233";
	}
}
