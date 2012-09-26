package se.arnetheduck.j2c.test;

public class Names {
	public class Private {
		private int init;

		private void init() {
			this.init = 6;
		}
	}

	public class Public {
		public int init;
		protected int m;

		public void init(int init) {
			this.init = init;
			init(init);
		}

		public void x(int x) {
			x(x);
		}
	}

	public class Derived extends Public {
		public int x;

		public void m() {
			this.init = 5;
			this.m = 6;
			int init = 5;
			init(init);
			int x = init;
			x(x); // need to find x in base class!
		}
	}

	public class Test {
		Object o;
	}

	public static class FieldNamedLikeClass {
		public static void m(Empty e) {
			// Field below might redefine meaning of name "Empty" if the class
			// reference remains unqualified here
			e = new Empty();
		}

		// static final field with same name as class
		public static final Empty Empty = new Empty();
	}

	public static class MethodNamedLikeClass {
		public static void m(Empty e) {
			// Field below might redefine meaning of name "Empty" if the class
			// reference remains unqualified here
			e = new Empty();
		}

		// static final field with same name as class
		public static void Empty() {
			Empty e = new Empty();
		}
	}
}

// Valid in Java but messes up filenames in Makefile
class Funny$Name {
}