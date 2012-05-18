package se.arnetheduck.j2c.transform;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.ITypeBinding;

// TODO ArrayStoreException
public class ArrayWriter {
	private final IPath root;
	private final Transformer ctx;
	private final ITypeBinding type;

	public ArrayWriter(IPath root, Transformer ctx, ITypeBinding type) {
		this.ctx = ctx;
		if (!type.isArray()) {
			throw new UnsupportedOperationException();
		}

		this.root = root;
		this.type = type;
	}

	private List<ITypeBinding> getSuperTypes() {
		ITypeBinding ct = type.getComponentType();
		if (ct.isPrimitive()
				|| ct.getQualifiedName().equals(Object.class.getName())) {
			return Collections.emptyList();
		}

		List<ITypeBinding> ret = new ArrayList<ITypeBinding>();

		ret.add((ct.getSuperclass() == null ? ctx.resolve(Object.class) : ct
				.getSuperclass()).createArrayType(1));

		for (ITypeBinding tb : ct.getInterfaces()) {
			ret.add(tb.createArrayType(1));
		}

		return ret;
	}

	public void write() throws IOException {
		ctx.headers.add(type);

		FileOutputStream fos = new FileOutputStream(root.append(
				TransformUtil.headerName(type)).toFile());
		PrintWriter pw = new PrintWriter(fos);

		pw.println("#pragma once");
		pw.println(TransformUtil.include("forward.h"));
		pw.println();

		String name = TransformUtil.name(type);
		ITypeBinding ct = type.getComponentType();
		String ret = TransformUtil.cname(TransformUtil.name(ct));

		List<ITypeBinding> types = getSuperTypes();

		if (types.isEmpty()) {
			ITypeBinding object = ctx.resolve(Object.class);
			ITypeBinding cln = ctx.resolve(Cloneable.class);
			ITypeBinding ser = ctx.resolve(Serializable.class);
			ctx.hardDep(object);
			ctx.hardDep(cln);
			ctx.hardDep(ser);
			ctx.hardDep(object.createArrayType(1));
			pw.println(TransformUtil.include(object));
			pw.println(TransformUtil.include(cln));
			pw.println(TransformUtil.include(ser));

			pw.println();
			pw.println("class " + TransformUtil.qualifiedCName(type, false));
			pw.println("    : public virtual ::java::lang::Object");
			pw.println("    , public virtual ::java::lang::Cloneable");
			pw.println("    , public virtual ::java::io::Serializable");
		} else {
			for (ITypeBinding tb : types) {
				ctx.hardDep(tb);
				pw.println(TransformUtil.include(tb));
			}

			pw.println();
			pw.println("class " + TransformUtil.qualifiedCName(type, false));

			String sep = "    : public virtual ";
			for (ITypeBinding tb : types) {
				pw.print(sep);
				sep = "    , public virtual ";
				pw.println(TransformUtil.relativeCName(tb, type, true));
			}
		}

		pw.println("{");
		pw.println("public:");
		pw.print(TransformUtil.indent(1));
		pw.println("static ::java::lang::Class *class_;");

		pw.println();
		pw.println("    " + ret + " " + TransformUtil.ref(ct)
				+ "&operator[](int i) { return static_cast<" + ret + " *"
				+ TransformUtil.ref(ct) + ">(p)[i]; }");

		pw.println("    " + name + "* clone() { return this; /* TODO */ }");
		pw.println();

		if (types.isEmpty()) {
			pw.println("    template<typename... T>");
			pw.println("    " + name + "(int n, T... args)");
			pw.println("        : length_(n), p(new Object[n]) { init(0, args...); }");
			pw.println();
			pw.println("    const int length_;");
			pw.println();
			pw.println("protected:");
			pw.println("    " + name + "(void * p, int length)");
			pw.println("        : p(p), length_(length) { }");
			pw.println();
			pw.println("    void *p;");
		} else {
			pw.println("    template<typename... T>");
			pw.println("        " + name + "(int n, T... args)");
			pw.println("        : ObjectArray(new " + ret
					+ TransformUtil.ref(ct) + "[n], n) { init(0, args...); }");

			pw.println("protected:");
			pw.println("    " + name + "()");
			pw.println("        : ObjectArray(0) { }");
		}

		pw.println();
		pw.println("private:");
		pw.println("    void init(int i) { }");
		pw.println("    template<typename T, typename... TRest>");
		pw.println("    void init(int i, T first, TRest... rest) { (*this)[i] = first; init(i+1, rest...); }");

		pw.println("};");

		pw.close();
	}
}
