package se.arnetheduck.j2c.test;

public class ClassLiteralTest {
	interface A {
	}

	Class<?> a = ClassLiteralTest.class;
	Class<?> b = ClassLiteralTest[].class;
	Class<?> c = int.class;
	Class<?> d = A.class;
}
