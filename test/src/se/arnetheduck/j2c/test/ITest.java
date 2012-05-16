package se.arnetheduck.j2c.test;

public interface ITest {
	int x = 5; // implicitly static & final

	public class NestedI {
		int x() {
			return 1;
		}
	}
}
