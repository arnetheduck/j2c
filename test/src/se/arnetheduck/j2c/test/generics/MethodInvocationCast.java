package se.arnetheduck.j2c.test.generics;

public class MethodInvocationCast {
	public interface I<X> {
		X m();
	}

	public interface B {
	}

	B m(I<? extends B> i) {
		return i.m();
	}
}
