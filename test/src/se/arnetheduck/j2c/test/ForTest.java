package se.arnetheduck.j2c.test;

import java.util.ArrayList;
import java.util.List;

public class ForTest {
	public void simple() {
		for (int i = 0; i < 10; ++i) {
			i = i + i;
		}
	}

	public void noBlock() {
		for (int i = 0; i < 10; ++i)
			i = i + 1;
	}

	public int enhancedArray() {
		int ret = 0;
		int[] x = new int[] { 0, 1, 2 };
		for (int i : x) {
			ret += i;
		}

		return ret;
	}

	public void enhancedList() {
		List<String> x = new ArrayList<String>();
		for (String i : x) {
			System.out.print(i);
		}
	}
}
