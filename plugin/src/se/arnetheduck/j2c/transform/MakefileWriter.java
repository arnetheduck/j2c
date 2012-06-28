package se.arnetheduck.j2c.transform;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;

import org.eclipse.core.runtime.IPath;

import se.arnetheduck.j2c.transform.MainWriter.Info;

public class MakefileWriter {
	private final IPath root;

	public MakefileWriter(IPath root) {
		this.root = root;
	}

	public void write(String name, Iterable<String> impls,
			Iterable<String> stubs, Iterable<String> natives,
			Collection<MainWriter.Info> mains) throws IOException {
		FileOutputStream fos = new FileOutputStream(root.append("Makefile")
				.toFile());
		PrintWriter pw = new PrintWriter(fos);

		pw.println("INCLUDES = ");
		pw.println("CPPFLAGS := $(CPPFLAGS) $(INCLUDES)");
		pw.println("CFLAGS := $(CFLAGS) -g -pipe");
		pw.println("CXXFLAGS := $(CFLAGS) -std=gnu++11");
		pw.println("SRCS = \\");

		for (String impl : impls) {
			pw.println("\t" + impl + " \\");
		}

		pw.println();
		pw.println("STUB_SRCS = \\");

		for (String stub : stubs) {
			pw.println("\t" + stub + " \\");
		}

		pw.println();
		pw.println("NATIVE_SRCS = \\");

		for (String n : natives) {
			pw.println("\t" + n + " \\");
		}

		pw.println();

		pw.println("OBJS = $(SRCS:.cpp=.o)");
		pw.println("STUB_OBJS = $(STUB_SRCS:.cpp=.o)");

		pw.println();

		pw.println("%.a:");
		pw.println("\trm -f $@");
		pw.println("\tar rcs $@ $^");
		pw.println();

		String libName = "lib" + name + ".a";
		String stubLibName = "lib" + name + TransformUtil.STUB + ".a";
		String nativeLibName = "lib" + name + TransformUtil.NATIVE + ".a";

		pw.println("all: " + libName + " " + stubLibName + " " + nativeLibName);
		pw.println();

		pw.println(libName + ": $(OBJS)");
		pw.println();

		pw.println(stubLibName + ": $(STUB_OBJS)");
		pw.println();

		pw.println(nativeLibName + ": $(NATIVE_OBJS)");
		pw.println();

		if (!mains.isEmpty()) {
			for (MainWriter.Info main : mains) {
				String mainName = main.filename;
				pw.format("%s: %s %s %s\n", main.qname, mainName, libName,
						stubLibName);

				pw.format(
						"\t$(CXX) $(CPPFLAGS) $(CXXFLAGS) -o $@ $(EXTRA) %1$s -L. -l%2$s $(LIBS) -l%2$s%3$s -l%2$s%4$s\n",
						mainName, name, TransformUtil.STUB,
						TransformUtil.NATIVE);
			}

			pw.print("mains: ");
			for (Info main : mains) {
				pw.print(main.qname);
				pw.print(" ");
			}
			pw.println();
		}

		pw.println();
		pw.println(".PHONY: all mains");

		pw.close();
	}
}
