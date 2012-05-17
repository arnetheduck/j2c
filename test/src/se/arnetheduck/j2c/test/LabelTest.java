package se.arnetheduck.j2c.test;

public class LabelTest {
	public void m() {
		outer: for (int i = 0; i < 10; ++i) {
			for (int j = 0; j < 10; ++j) {
				if (j > 5 && i % 2 == 0) {
					break outer;
				} else if (j > 5 && i % 2 == 1) {
					continue outer;
				}
			}
		}

		test: {
			for (int i = 0; i < 10; ++i) {
				break test;
			}
		}
	}
}
