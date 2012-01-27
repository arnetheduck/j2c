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

		pw.println("    " + name + "(int i) : length_(i) { }");

		pw.println("    " + ret + " " + TransformUtil.ref(ct)
				+ "&operator[](int i);");
		pw.println("    int length_;");
		pw.println("};");

		pw.close();
	}
}
