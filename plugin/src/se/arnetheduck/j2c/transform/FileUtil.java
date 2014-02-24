package se.arnetheduck.j2c.transform;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Scanner;

public class FileUtil {
	public static void copy(File source, File target) throws IOException {
		InputStream is = null;
		OutputStream os = null;
		byte[] buf = new byte[4096];
		try {
			is = new FileInputStream(source);
			try {
				os = new FileOutputStream(target);
				for (int n = is.read(buf); n != -1; n = is.read(buf)) {
					os.write(buf, 0, n);
				}
			} finally {
				is.close();
			}
		} finally {
			if (os != null) {
				os.close();
			}
		}
	}

	public static String readResource(String resource) {
		InputStream is = null;
		try {
			is = FileUtil.class.getResourceAsStream(resource);
			return FileUtil.read(is);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}

	private static String read(InputStream resource) {
		return new Scanner(resource).useDelimiter("\\A").next();
	}

	public static PrintWriter open(File target) throws FileNotFoundException {
		if (!target.getParentFile().exists()) {
			target.getParentFile().mkdirs();
		}

		FileOutputStream fos = new FileOutputStream(target);
		return new PrintWriter(fos);
	}

	public static void writeResource(String name, File target)
			throws IOException {
		InputStream is = null;
		try {
			is = FileUtil.class.getResourceAsStream(name);
			writeResource(is, target);
		} finally {
			if (is != null) {
				is.close();
			}
		}
	}

	private static void writeResource(InputStream resource, File target)
			throws IOException {
		PrintWriter pw = null;
		String txt = FileUtil.read(resource);

		try {
			pw = open(target);
			pw.write(txt);
		} finally {
			if (pw != null) {
				pw.close();
			}
		}
	}

	public static void writeTemplate(String resource, File target,
			Object... params) throws IOException {
		InputStream is = null;
		try {
			is = FileUtil.class.getResourceAsStream(resource);
			writeTemplate(is, target, params);
		} finally {
			if (is != null) {
				is.close();
			}
		}
	}

	private static void writeTemplate(InputStream template, File target,
			Object... params) throws IOException {
		String format = read(template);

		PrintWriter pw = null;
		try {
			pw = open(target);
			pw.format(format, params);
		} finally {
			if (pw != null) {
				pw.close();
			}
		}
	}
}
