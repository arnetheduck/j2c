package se.arnetheduck.j2c.test.enums;

// Test that we get values and valueOf when generics are present
public class ImplicitMethods<T> {
	enum Y {
		X
	}

	void m() {
		Y x = Y.valueOf("X");
		Y[] y = Y.values();
	}
}
