package se.arnetheduck.j2c.test.generics;

public class Raw {
	Fields.A a;
	Implement.I i;

	void m() {
		a.t.notify();
		i.m2();
		i.m(null);
	}
}
