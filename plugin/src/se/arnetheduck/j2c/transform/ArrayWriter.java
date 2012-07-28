package se.arnetheduck.j2c.transform;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

public class ArrayWriter {
	private static final String ARRAY_HPP = "/se/arnetheduck/j2c/resources/Array.hpp";
	private static final String OBJECT_ARRAY_HPP = "/se/arnetheduck/j2c/resources/ObjectArray.hpp";
	private static final String SUB_ARRAY_HPP_TMPL = "/se/arnetheduck/j2c/resources/SubArray.hpp.tmpl";

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
		ctx.softDep(type);
		ctx.hardDep(ctx.resolve(Cloneable.class));
		ctx.hardDep(ctx.resolve(Serializable.class));
		ctx.hardDep(ctx.resolve(ArrayStoreException.class));
		writeHeader();
		writeImpl();
	}

	public void writeHeader() throws IOException {
		File target = TransformUtil.headerPath(root, type).toFile();

		ITypeBinding ct = type.getComponentType();
		if (ct.isPrimitive()) {
			try (InputStream is = ArrayWriter.class
					.getResourceAsStream(ARRAY_HPP)) {
				TransformUtil.writeResource(is, target);
			}

			return;
		} else if (ct.getQualifiedName().equals(Object.class.getName())) {
			try (InputStream is = ArrayWriter.class
					.getResourceAsStream(OBJECT_ARRAY_HPP)) {
				TransformUtil.writeResource(is, target);
			}

			return;
		}

		List<ITypeBinding> types = getSuperTypes();

		StringBuilder includes = new StringBuilder();

		for (ITypeBinding tb : types) {
			ctx.hardDep(tb);
			includes.append(TransformUtil.include(tb) + "\n");
		}

		String name = CName.of(ct);
		String qname = CName.qualified(ct, false);
		String superName = ct.getSuperclass() == null ? "::java::lang::Object"
				: CName.relative(ct.getSuperclass(), ct, true);
		ctx.hardDep(ct);
		includes.append(TransformUtil.include(ct) + "\n");

		StringBuilder bases = new StringBuilder();

		String sep = "    : public virtual ";
		for (ITypeBinding tb : types) {
			bases.append(sep);
			sep = "    , public virtual ";
			bases.append(CName.relative(tb, type, true));
			bases.append("\n");
		}

		try (InputStream is = ArrayWriter.class
				.getResourceAsStream(SUB_ARRAY_HPP_TMPL)) {
			TransformUtil.writeTemplate(is, target, includes, name, bases,
					superName, qname);
		}
	}

	private void writeImpl() throws IOException {
		ctx.addImpl(type);
		Impl impl = new Impl(ctx, type, new DepInfo(ctx));

		impl.write(root, "", "", new ArrayList<IVariableBinding>(), null,
				false, false);
	}
}
