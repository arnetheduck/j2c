package se.arnetheduck.j2c.transform;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.ITypeBinding;

public class MainWriter {
	private static final String MAIN_CPP_TMPL = "/se/arnetheduck/j2c/resources/Main.cpp.tmpl";

	public static class Info {
		public final String filename;
		public final String include;
		public final String qcname;
		public final String qname;

		public Info(ITypeBinding tb) {
			filename = TransformUtil.mainName(tb);
			include = TransformUtil.include(tb);
			qcname = TransformUtil.qualifiedCName(tb, true);
			qname = TransformUtil.qualifiedName(tb);
		}
	}

	public static void write(IPath root, Info info) throws IOException {
		try (InputStream is = MainWriter.class
				.getResourceAsStream(MAIN_CPP_TMPL)) {
			File target = root.append(info.filename).toFile();
			TransformUtil.writeTemplate(is, target, info.include, info.qcname);
		}
	}
}
