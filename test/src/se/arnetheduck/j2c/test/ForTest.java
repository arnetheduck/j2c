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

	public int enhancedObjectArray() {
		int ret = 0;
		Object[] x = new Object[] { null };
		for (Object i : x) {
			ret++;
		}

		return ret;
	}

	public int enhancedStringArray() {
		int ret = 0;
		String[] x = new String[] { null };
		for (String i : x) {
			ret++;
		}

		return ret;
	}

	public Empty enhancedList() {
		List<Empty> x = new ArrayList<Empty>();
		for (Empty i : x) {
			return i;
		}
		return null;
	}

	public int enhancedBox() {
		List<Integer> x = new ArrayList<Integer>();
		for (int i : x) {
			return i;
		}
		return 0;
	}
}
