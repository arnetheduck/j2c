package se.arnetheduck.j2c.test;

public class CastTest {
	int m(int a, int b) {
		return a > b ? a : b;
	}

	double m(double a, double b) {
		return a > b ? a : b;
	}

	double x() {
		return m(1, 1.0);
	}

	double y1() {
		return 1. % 5;
	}

	float y2() {
		return 1.f % 5;
	}

	double y3() {
		return 1 % 5.;
	}

	float y4() {
		return 1 % 5.f;
	}

}
