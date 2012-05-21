package se.arnetheduck.j2c.test.nesting;

public class DoubleAnon<V> {
	public Object n() {
		return new Object() {
			final Object i = null;

			public Object next() {
				return new Object() {
					String k = i.toString();
				};
			}
		};
	}
}
