package se.arnetheduck.j2c.test;

public class LocalClassTest {
	LocalClassTest lct;

	public int testLocalParamConstructor() {
		ParamConstructor lpc = new ParamConstructor(3) {
			@Override
			public int getV() {
				return 5;
			}
		};

		return lpc.getV();
	}

	public int testClosure(final int p) {
		ParamConstructor lpc = new ParamConstructor(3) {
			@Override
			public int getV() {
				return p;
			}
		};

		return lpc.getV();
	}

	public int testClassClosure(final Empty p) {
		ParamConstructor lpc = new ParamConstructor(3) {
			@Override
			public int getV() {
				return p.hashCode();
			}
		};

		return lpc.getV();
	}

	public int testArrayAccess() {
		ParamConstructor lpc = new ParamConstructor(3) {
			@Override
			public int run(int[] x) {
				return testClosure(x[0]);
			}
		};

		return lpc.run(new int[] { 5 });
	}

	public int testSameClassAccess() {
		ParamConstructor lpc = new ParamConstructor(3) {
			@Override
			public int run(int[] x) {
				LocalClassTest same = lct;
				return same.testClosure(4);
			}
		};

		return lpc.run(null);
	}

	public int testNestedClass() {
		ParamConstructor lpc = new ParamConstructor(3) {
			@Override
			public int run(int[] x) {
				ParamConstructor lpc2 = new ParamConstructor(3) {
					@Override
					public int run(int[] x) {
						return lct.testClosure(x[0]);
					}
				};

				return lpc2.run(new int[] { 10 });
			}
		};

		return lpc.run(new int[] { 10 });
	}

	/*
	public int testNestedClosure(final int outermost) {
		ParamConstructor lpc = new ParamConstructor(3) {
			@Override
			public int run(int[] x) {
				ParamConstructor lpc2 = new ParamConstructor(3) {
					@Override
					public int run(int[] x) {
						return x[0] + outermost;
					}
				};

				return lpc2.run(new int[] { 10 });
			}
		};

		return lpc.run(new int[] { 10 });

	} */
}
