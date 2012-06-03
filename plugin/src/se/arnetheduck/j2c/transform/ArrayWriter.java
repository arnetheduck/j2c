package se.arnetheduck.j2c.transform;

import java.io.FileNotFoundException;
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
		writeHeader();
		writeImpl();
	}

	public void writeHeader() throws FileNotFoundException {
		ctx.headers.add(type);

		FileOutputStream fos = new FileOutputStream(root.append(
				TransformUtil.headerName(type)).toFile());
		PrintWriter pw = new PrintWriter(fos);

		pw.println("#pragma once");
		pw.println();
		pw.println(TransformUtil.include("forward.h"));
		pw.println("#include <string.h>");

		pw.println();

		String name = TransformUtil.name(type);
		ITypeBinding ct = type.getComponentType();
		String ret = TransformUtil.cname(TransformUtil.name(ct));

		List<ITypeBinding> types = getSuperTypes();

		String storage = ct.isPrimitive() ? TransformUtil.cname(TransformUtil
				.name(ct)) : "Object*";

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

			if (!ct.isPrimitive()) {
				ctx.hardDep(ct);
				pw.println(TransformUtil.include(ct));
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
		pw.println("    " + name + "* clone() { return new " + name
				+ "(*this); }");

		pw.println();

		if (ct.isPrimitive()) {
			pw.println("    " + ret + " &operator[](int i) { return p[i]; }");

			pw.println("    " + ret + " get(int i) const { return p[i]; }");

			pw.println("    " + ret + "& set(int i, " + ret
					+ " x) { return (p[i] = x); }");
		} else {
			pw.println("    " + ret
					+ "* operator[](int i) const { return get(i); }");

			pw.println("    " + ret
					+ "* get(int i) const { return dynamic_cast<" + ret
					+ TransformUtil.ref(ct) + ">(p[i]); }");

			pw.println("    " + ret + "* set(int i, " + ret
					+ " *x) { p[i] = x; return x; }");
		}

		pw.println();

		if (types.isEmpty()) {
			pw.println("    template<typename... T>");
			pw.println("    " + name + "(int n, T... args)");
			pw.println("        : length_(n), p(new " + storage
					+ "[n]) { init(0, args...); }");
			pw.println();
			pw.println("    " + name + "(const " + name + " &rhs)");
			pw.println("        : length_(rhs.length_), p(new " + storage
					+ "[rhs.length_])");
			pw.println("    { memcpy(p, rhs.p, length_ * sizeof(" + storage
					+ ")); }");
			pw.println();
			pw.println("    virtual ~" + name + "() { delete [] p; }");
			pw.println();

			pw.println("    const int length_;");
			pw.println();

			if (ct.isPrimitive()) {
				pw.println("    " + name + "(const " + storage + " *p, int n)");
				pw.println("        : length_(n), p(new " + storage
						+ "[n]) { memcpy(this->p, p, n * sizeof(" + storage
						+ ")); }");
				pw.println("    " + storage + " *p;");
			} else {
				pw.println("    " + name + "(int length)");
				pw.println("        : length_(length), p(new " + storage
						+ "[length]) { }");
				pw.println();
				pw.println("protected:");
				pw.println("    " + storage + " *p;");
			}
		} else {
			pw.println("    template<typename... T>");
			pw.println("    " + name + "(int n, T... args)");
			pw.println("        : ObjectArray(n) { init(0, args...); }");

			pw.println("protected:");
			pw.println("    " + name + "()");
			pw.println("        : ObjectArray(0) { }");
		}

		pw.println();
		pw.println("private:");
		pw.println("    void init(int i) { }");
		pw.println("    template<typename T, typename... TRest>");
		pw.println("    void init(int i, T first, TRest... rest) { set(i, first); init(i+1, rest...); }");

		pw.println("};");

		pw.close();
	}

	private void writeImpl() throws IOException {
		ctx.impls.add(type);
		PrintWriter pw = TransformUtil.openImpl(root, type, "");

		pw.print("::java::lang::Class *");
		pw.print(TransformUtil.qualifiedCName(type, false));
		pw.println("::class_ = 0;");

		pw.close();
	}
}
