package se.arnetheduck.j2c.transform;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;

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

	public ITypeBinding getSuperType() {
		ITypeBinding ct = type.getComponentType();
		if (ct.isPrimitive()
				|| ct.getQualifiedName().equals(Object.class.getName())) {
			return null;
		}

		ITypeBinding sb = ct.getSuperclass() == null ? ctx
				.resolve(Object.class) : ct.getSuperclass();
		return sb.createArrayType(1);
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
		String cname = TransformUtil.qualifiedCName(type, false);
		ITypeBinding ct = type.getComponentType();
		String ret = TransformUtil.cname(TransformUtil.name(ct));

		ITypeBinding sb = getSuperType();

		String parentType = sb == null ? null : TransformUtil.relativeCName(sb,
				type, true);

		if (sb == null) {
			ITypeBinding object = ctx.resolve(Object.class);
			ITypeBinding cln = ctx.resolve(Cloneable.class);
			ITypeBinding ser = ctx.resolve(Serializable.class);
			ctx.hardDep(object);
			ctx.hardDep(cln);
			ctx.hardDep(ser);
			pw.println(TransformUtil.include(object));
			pw.println(TransformUtil.include(cln));
			pw.println(TransformUtil.include(ser));
		} else {
			ctx.hardDep(sb);
			pw.println(TransformUtil.include(sb));
		}

		pw.println();

		pw.println("class " + cname);
		if (sb == null) {
			pw.println("    : public virtual ::java::lang::Object");
			pw.println("    , public virtual ::java::lang::Cloneable");
			pw.println("    , public virtual ::java::io::Serializable");
		} else {
			pw.println("    : public " + parentType);
		}

		pw.println("{");
		pw.println("public:");
		pw.print(TransformUtil.indent(1));
		pw.println("static ::java::lang::Class *class_;");

		pw.println("    template<typename... T>");
		if (sb == null) {
			pw.println("    " + name
					+ "(int n, T... args) : length_(n), p(new " + ret
					+ TransformUtil.ref(ct) + "[n]) { init(0, args...); }");
		} else {
			pw.println("    " + name + "(int n, T... args) : " + parentType
					+ "(new " + ret + TransformUtil.ref(ct)
					+ "[n], n) { init(0, args...); }");
		}

		pw.println();
		pw.println("    " + ret + " " + TransformUtil.ref(ct)
				+ "&operator[](int i) { return static_cast<" + ret + " *"
				+ TransformUtil.ref(ct) + ">(p)[i]; }");

		pw.print("    " + name + "* clone() { return this; /* TODO */ }");

		if (sb == null) {
			pw.println();
			pw.println("    const int length_;");
			pw.println();
			pw.println("protected:");
			pw.println("    " + name
					+ "(void * p, int length) : p(p), length_(length) { }");
			pw.println("    void *p;");
		} else {
			pw.println("protected:");
			pw.println("    " + name + "(void * p, int length) : " + parentType
					+ "(p, length) { }");
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
