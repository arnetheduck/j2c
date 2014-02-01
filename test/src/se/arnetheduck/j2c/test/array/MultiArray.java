package se.arnetheduck.j2c.test.array;

public class MultiArray {
	int[][] a = new int[1][];
	int[][][] b = new int[1][][];

	MultiArray() {
		a = new int[1][1];
		b = new int[1][a.length][2];
	}
}
