package se.arnetheduck.j2c.test.init;

public class ConstExpr {
	final boolean bi = false;
	final char ci = 'c';
	final double di = 0.1;
	final float fi = 0.1f;
	final int ii = 5;
	final long li = 5l;
	final short si = 4;

	static final boolean sbi = false;
	static final char sci = 'c';
	static final double sdi = 0.1;
	static final float sfi = 0.1f;
	static final int sii = 5;
	static final long sli = 5l;
	static final short ssi = 4;

	// Constexpr in java but not c++
	final String s = "hello";
	static final String ss = "helloToo";

	final Boolean bbi = false;
	final Character bci = 'c';
	final Double bdi = 0.1;
	final Float bfi = 0.1f;
	final Integer bii = 5;
	final Long bli = 5l;
	final Short bsi = 4;

	static final Boolean bsbi = false;
	static final Character bsci = 'c';
	static final Double bsdi = 0.1;
	static final Float bsfi = 0.1f;
	static final Integer bsii = 5;
	static final Long bsli = 5l;
	static final Short bssi = 4;

	void m(Object... o) {
		m(bi, ci, di, fi, ii, li, si, s, ss);
	}
}
