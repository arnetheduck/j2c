package se.arnetheduck.j2c.test;

public class LocalClassTest {
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
}
