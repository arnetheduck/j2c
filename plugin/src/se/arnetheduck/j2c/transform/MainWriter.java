package se.arnetheduck.j2c.transform;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.ITypeBinding;

public class MainWriter {
	private final IPath root;
	private final Transformer ctx;
	private final ITypeBinding type;

	public MainWriter(IPath root, Transformer ctx, ITypeBinding type) {
		this.ctx = ctx;
		this.root = root;
		this.type = type;
	}

	public void write() throws IOException {
		FileOutputStream fos = new FileOutputStream(root.append(
				TransformUtil.mainName(type)).toFile());

		PrintWriter pw = new PrintWriter(fos);

		pw.println(TransformUtil.include(type));
		pw.println();

		pw.println("int main(int, char**)");
		pw.println("{");
		pw.print(TransformUtil.indent(1));
		pw.print(TransformUtil.qualifiedCName(type));
		pw.println("::main(0);");
		pw.print(TransformUtil.indent(1));
		pw.println("return 0;");
		pw.print("}");
		pw.println();
	}
}
