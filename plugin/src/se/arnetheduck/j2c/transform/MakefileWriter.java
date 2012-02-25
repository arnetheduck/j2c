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

	public void write(Iterable<ITypeBinding> types,
			Iterable<ITypeBinding> stubs, Collection<ITypeBinding> mains)
			throws IOException {
		FileOutputStream fos = new FileOutputStream(root.append("Makefile")
				.toFile());
		PrintWriter pw = new PrintWriter(fos);

		pw.println("CXXFLAGS = -std=gnu++0x");
		pw.println("SRCS = \\");

		for (ITypeBinding tb : types) {
			if (tb.isNullType() || mains.contains(tb)) {
				continue;
			}

			pw.print("    ");
			pw.print(TransformUtil.implName(tb));
			pw.println(" \\");
		}

		pw.println();
		pw.println("STUB_SRCS = \\");

		for (ITypeBinding tb : stubs) {
			if (tb.isNullType() || mains.contains(tb)) {
				continue;
			}

			pw.print("    ");
			pw.print(TransformUtil.implName(tb));
			pw.println(" \\");
		}

		pw.println();

		pw.println("OBJS = $(SRCS:.cpp=.o)");
		pw.println("STUB_OBJS = $(STUB_SRCS:.cpp=.o)");

		pw.println();

		if (mains.isEmpty()) {
			pw.println("all: $(OBJS) $(STUB_OBJS)");
			pw.println();
		} else {
			for (ITypeBinding main : mains) {
				pw.print(TransformUtil.qualifiedName(main));
				pw.print(": $(OBJS) $(STUB_OBJS) ");
				pw.println(TransformUtil.objName(main));
				pw.println("	g++ -o $@ $(EXTRA) $^ $(CFLAGS) $(LIBS)");
				pw.println();
			}

			pw.print("all: ");
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
