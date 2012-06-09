package se.arnetheduck.j2c.transform;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

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

	public void writeHeader() throws IOException {
		ctx.headers.add(type);

		ITypeBinding ct = type.getComponentType();
		if (ct.isPrimitive()) {
			try (InputStream is = ArrayWriter.class
					.getResourceAsStream("/se/arnetheduck/j2c/resources/Array.h")) {
				Files.copy(is, root.append(TransformUtil.headerName(type))
						.toFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
			}

			return;
		} else if (ct.getQualifiedName().equals(Object.class.getName())) {
			try (InputStream is = ArrayWriter.class
					.getResourceAsStream("/se/arnetheduck/j2c/resources/ObjectArray.h")) {
				Files.copy(is, root.append(TransformUtil.headerName(type))
						.toFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
			}

			return;
		}

		File target = root.append(TransformUtil.headerName(type)).toFile();

		List<ITypeBinding> types = getSuperTypes();

		StringBuilder includes = new StringBuilder();

		for (ITypeBinding tb : types) {
			ctx.hardDep(tb);
			includes.append(TransformUtil.include(tb) + "\n");
		}

		String name = TransformUtil.name(ct);
		String qname = TransformUtil.qualifiedCName(ct, false);
		String superName = ct.getSuperclass() == null ? "::java::lang::Object"
				: TransformUtil.relativeCName(ct.getSuperclass(), ct, true);
		ctx.hardDep(ct);
		includes.append(TransformUtil.include(ct) + "\n");

		StringBuilder bases = new StringBuilder();

		String sep = "    : public virtual ";
		for (ITypeBinding tb : types) {
			bases.append(sep);
			sep = "    , public virtual ";
			bases.append(TransformUtil.relativeCName(tb, type, true));
			bases.append("\n");
		}

		try (InputStream is = ArrayWriter.class
				.getResourceAsStream("/se/arnetheduck/j2c/resources/SubArray.h.tmpl")) {
			String template = new Scanner(is).useDelimiter("\\A").next();

			try (PrintWriter pw = new PrintWriter(new FileOutputStream(target))) {
				pw.format(template, includes, name, bases, superName, qname);
			}
		}
	}

	private void writeImpl() throws IOException {
		ctx.impls.add(type);
		PrintWriter pw = TransformUtil.openImpl(root, type, "");

		TransformUtil.printClassLiteral(pw, type);
		TransformUtil.printGetClass(pw, type);

		pw.close();
	}

}
