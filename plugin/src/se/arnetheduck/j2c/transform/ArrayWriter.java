package se.arnetheduck.j2c.transform;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.ITypeBinding;

public class ArrayWriter {
	private final IPath root;

	public ArrayWriter(IPath root) {
		this.root = root;
	}

	public void write(ITypeBinding tb) throws IOException {
		if (!tb.isArray()) {
			return;
		}

		FileOutputStream fos = new FileOutputStream(root.append(
				TransformUtil.headerName(tb)).toFile());
		PrintWriter pw = new PrintWriter(fos);

		pw.println("#pragma once");
		pw.println(TransformUtil.include("forward.h"));
		pw.println();

		String name = TransformUtil.name(tb);
		String qname = TransformUtil.qualifiedName(tb);
		String cname = TransformUtil.cname(qname);
		ITypeBinding ct = tb.getComponentType();
		String ret = TransformUtil.cname(TransformUtil.name(ct));

		pw.println("class " + cname + " : public java::lang::Object {");
		pw.println("public:");

		pw.println("    template<typename... T>");
		pw.println("    " + name + "(int n, T... args) : length_(n), p(new "
				+ ret + TransformUtil.ref(ct) + "[n]) { init(0, args...); }");

		pw.println();
		pw.println("    void init(int i) { }");
		pw.println("    template<typename T, typename... TRest>");
		pw.println("    void init(int i, T first, TRest... rest) { (*this)[i] = first; init(i+1, rest...); }");

		pw.println();
		pw.println("    " + ret + " " + TransformUtil.ref(ct)
				+ "&operator[](int i) { return p[i]; }");

		pw.println();
		pw.println("    int length_;");

		pw.println("    " + ret + " *" + TransformUtil.ref(ct) + "p;");
		pw.println("};");

		pw.close();
	}
}
