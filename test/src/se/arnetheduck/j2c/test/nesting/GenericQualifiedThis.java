package se.arnetheduck.j2c.test.nesting;

// Check that A.this works even when top level is generic
public class GenericQualifiedThis<T> {
	public class A {
		public class B {
			public A map() {
				return A.this;
			}
		}
	}
}
