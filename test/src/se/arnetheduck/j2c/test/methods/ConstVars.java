package se.arnetheduck.j2c.test.methods;

// Local variables can be both const and constexpr
public class ConstVars {
	int m(final int x) {
		final int y = x;
		final int z = 5;
		switch (z) {
		case z:
			return y;
		}

		return 0;
	}
}
