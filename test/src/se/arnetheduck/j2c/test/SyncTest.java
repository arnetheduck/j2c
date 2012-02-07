package se.arnetheduck.j2c.test;

public class SyncTest {
	public int m(int x) {
		synchronized (this) {
			return x * 6;
		}
	}
}
