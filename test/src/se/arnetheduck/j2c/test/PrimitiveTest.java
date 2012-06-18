package se.arnetheduck.j2c.test;

public class PrimitiveTest {
	boolean b;
	char c;
	double d;
	float f;
	int i;
	long l;
	short s;

	boolean bi = false;
	char ci = 'c';
	double di = 0.1;
	float fi = 0.1f;
	int ii = 5;
	long li = 5l;
	short si = 4;

	static boolean sb;
	static char sc;
	static double sd;
	static float sf;
	static int six;
	static long sl;
	static short ss;

	static boolean sbi = false;
	static char sci = 'c';
	static double sdi = 0.1;
	static float sfi = 0.1f;
	static int sii = 5;
	static long sli = 5l;
	static short ssi = 4;

	void f() {
		float f0 = 1;
		float f1 = 2;
		float f2 = f0 % f1; // fmod in c++
		f2 %= f1;
	}

	void d() {
		double f0 = 1;
		double f1 = 2;
		double f2 = f0 % f1; // fmod in c++
		f2 %= f1;
	}

}
