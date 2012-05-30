package se.arnetheduck.j2c.test;

public class LabelTest {
	public void nestedFor() {
		outer: for (int i = 0; i < 10; ++i) {
			mid: for (int j = 0; j < 10; ++j) {
				for (int k = 0; k < 10; ++k) {
					if (j > 5 && i % 2 == 0) {
						break mid;
					} else if (j > 5 && i % 2 == 1) {
						continue outer;
					}
					int x = 5;
				}
				int y = 5;
			}
		}
	}

	public void breakBlock() {
		test: {
			for (int i = 0; i < 10; ++i) {
				break test;
			}

			int x = 5;
		}
	}

	public void contFor() {
		test: for (int i = 0; i < 10; ++i) {
			for (int j = i; j < 10; ++j) {
				continue test;
			}

			int x = 5;
		}
	}

	public void breakFor() {
		test: for (int i = 0; i < 10; ++i) {
			for (int j = i; j < 10; ++j) {
				break test;
			}

			int x = 5;
		}
	}

	public void contSimple() {
		test: for (int i = 0; i < 10; ++i) {
			continue test;
		}
	}

	public void breakSimple() {
		test: for (int i = 0; i < 10; ++i) {
			break test;
		}
	}

	public void breakEnhForIter(Iterable<?> i) {
		outer: for (Object o : i) {
			for (Object o2 : i) {
				break outer;
			}
		}
	}

	public void contEnhForIter(Iterable<?> i) {
		outer: for (Object o : i) {
			for (Object o2 : i) {
				continue outer;
			}
		}
	}

	public void breakEnhForArray(Object[] i) {
		outer: for (Object o : i) {
			for (Object o2 : i) {
				break outer;
			}
		}
	}

	public void contEnhForArray(Object[] i) {
		outer: for (Object o : i) {
			for (Object o2 : i) {
				continue outer;
			}
		}
	}

	public void sameLabel() {
		test: {
		}
		test: {
		}
	}
}
