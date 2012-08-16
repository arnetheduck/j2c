package se.arnetheduck.j2c.test;

public class Names {
	public class Private {
		private int init;

		private void init() {
		}
	}

	public class Public {
		public int init;

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
			int init = 5;
			init(init);
			int x = init;
			x(x); // need to find x in base class!
		}
	}

	public class Test {
		Object o;
	}
}

class Funny$Name {
}