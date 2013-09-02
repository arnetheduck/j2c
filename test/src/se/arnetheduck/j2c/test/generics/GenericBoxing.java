package se.arnetheduck.j2c.test.generics;

public class GenericBoxing {
	public interface T<X> {
		X value();
	}

	long m(T<? extends Long> t) {
		// Auto-unboxing should happen as X is guaranteed to extend Long
		return t.value();
	}
}
