package se.arnetheduck.j2c.test.methods;

public class CovariantCircular {
	public class A {
		public Object a() {
			return null;
		}

		public Object b() {
			return null;
		}
	}

	public class B extends A {
		@Override
		public C a() { // Covariant to subtype
			return null;
		}

		@Override
		public B b() { // Covariant to self
			return null;
		}
	}

	public class C extends B {

	}
}
