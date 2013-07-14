package se.arnetheduck.j2c.test.methods;

public class AmbiguousOverloads {
	public AmbiguousOverloads(AmbiguousOverloads x) {
	}

	public AmbiguousOverloads(long v) {
	}

	void m() {
		new AmbiguousOverloads(0); // ptr vs int ambiguity
		new AmbiguousOverloads(0L); // ptr vs long ambiguity
	}
}
