package se.arnetheduck.j2c.transform;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.ITypeBinding;

public class MakefileWriter {
	private final IPath root;

	public MakefileWriter(IPath root) {
		this.root = root;
	}

	public void write(String name, Iterable<ITypeBinding> types,
			Iterable<ITypeBinding> stubs, Iterable<ITypeBinding> natives,
			Collection<ITypeBinding> mains) throws IOException {
		FileOutputStream fos = new FileOutputStream(root.append("Makefile")
				.toFile());
		PrintWriter pw = new PrintWriter(fos);

		pw.println("CXXFLAGS = $(CFLAGS) -g -std=gnu++11");
		pw.println("SRCS = \\");

		for (ITypeBinding tb : types) {
			if (tb.isNullType()) {
				continue;
			}

			pw.print("    ");
			pw.print(TransformUtil.implName(tb, ""));
			pw.println(" \\");
		}

		pw.println();
		pw.println("STUB_SRCS = \\");

		for (ITypeBinding tb : stubs) {
			if (tb.isNullType()) {
				continue;
			}

			pw.print("    ");
			pw.print(TransformUtil.implName(tb, TransformUtil.STUB));
			pw.println(" \\");
		}

		pw.println();
		pw.println("NATIVE_SRCS = \\");

		for (ITypeBinding tb : natives) {
			if (tb.isNullType()) {
				continue;
			}

			pw.print("    ");
			pw.print(TransformUtil.implName(tb, TransformUtil.NATIVE));
			pw.println(" \\");
		}

		pw.println();

		pw.println("OBJS = $(SRCS:.cpp=.o)");
		pw.println("STUB_OBJS = $(STUB_SRCS:.cpp=.o)");

		pw.println();

		pw.println("%.a:");
		pw.println("	ar rcs $@ $^");
		pw.println("	ranlib $@");
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
			for (ITypeBinding main : mains) {
				String mainName = TransformUtil.mainName(main);
				pw.print(TransformUtil.qualifiedName(main) + ": ");
				pw.println(mainName + " " + libName + " " + stubLibName);
				pw.println("	g++ -o $@ $(EXTRA) " + mainName
						+ " $(CFLAGS) -L. -l" + name + " $(LIBS) -l" + name
						+ TransformUtil.STUB + " -l" + name
						+ TransformUtil.NATIVE);
				pw.println();
			}

			pw.print("mains: ");
			for (ITypeBinding main : mains) {
				pw.print(TransformUtil.qualifiedName(main));
				pw.print(" ");
			}
			pw.println();
		}

		pw.println();
		pw.println(".PHONY: all");

		pw.close();
	}
}
