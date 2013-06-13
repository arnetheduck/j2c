package se.arnetheduck.j2c.transform;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.TreeSet;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.ITypeBinding;

public class ForwardWriter {
	public static class Info implements Comparable<Info> {
		public String packageName;
		public String className;
		public boolean isInterface;
		public boolean isPrimitive;
		public boolean isPrimitiveArray;

		public Info(ITypeBinding tb) {
			isInterface = tb.isInterface();
			isPrimitive = tb.isPrimitive();
			isPrimitiveArray = TransformUtil.isPrimitiveArray(tb);

			packageName = CName.packageOf(tb);
			className = CName.of(isPrimitiveArray ? tb.getComponentType() : tb);
		}

		@Override
		public int compareTo(Info o) {
			int c = packageName.compareTo(o.packageName);
			c = c == 0 ? -Boolean.valueOf(isPrimitive).compareTo(o.isPrimitive)
					: c;
			return c == 0 ? className.compareTo(o.className) : c;
		}
	}

	private final IPath root;

	private Stack<String> cur = new Stack<String>();

	private final Transformer ctx;

	public ForwardWriter(Transformer ctx, IPath root) {
		this.ctx = ctx;
		this.root = root;
	}

	public void write(Collection<Info> infos) throws IOException {
		Map<String, TreeSet<Info>> types = new HashMap<String, TreeSet<Info>>();
		for (Info info : infos) {
			TreeSet<Info> l = types.get(info.packageName);
			if (l == null) {
				types.put(info.packageName, l = new TreeSet<Info>());
			}

			l.add(info);
		}

		for (Map.Entry<String, TreeSet<Info>> e : types.entrySet()) {
			boolean hasArray = false;
			boolean hasPrimitive = false;

			PrintWriter pw = null;
			try {
				pw = FileUtil.open(root
						.append("src")
						.append(TransformUtil.packageHeader(ctx.getName(),
								e.getKey())).toFile());

				pw.println("// Forward declarations for " + e.getKey());

				pw.println("#pragma once");
				pw.println();

				TransformUtil.setNs(pw,
						e.getKey().length() == 0 ? new ArrayList<String>()
								: Arrays.asList(e.getKey().split("\\.")), cur);

				String i = TransformUtil.indent(cur.size());
				for (Info info : e.getValue()) {
					if (info.isPrimitive) {
						if (!hasPrimitive) {
							pw.println("#include <stdint.h>");
							pw.println("#include <limits>");
							pw.println();

							hasPrimitive = true;
						}

						continue;
					}

					if (info.isPrimitiveArray) {
						if (!hasArray) {
							pw.println(i + "template<typename T> class Array;");
							hasArray = true;
						}

						pw.format("%stypedef Array<%2$s> %2$sArray;\n", i,
								info.className);

						continue;
					}

					pw.format("%s%s %s;\n", i, info.isInterface ? "struct"
							: "class", info.className);
				}

				TransformUtil.setNs(pw, new ArrayList<String>(), cur);
			} finally {
				if (pw != null) {
					pw.close();
				}
			}
		}
	}
}
