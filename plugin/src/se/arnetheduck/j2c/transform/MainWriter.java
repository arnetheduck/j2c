package se.arnetheduck.j2c.transform;

import java.io.File;
import java.io.IOException;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.ITypeBinding;

public class MainWriter {
	private static final String MAIN_CPP_TMPL = "/se/arnetheduck/j2c/resources/Main.cpp.tmpl";

	public static class Info {
		public final String filename;
		public final String include;
		public final String qcname;

		public Info(ITypeBinding tb) {
			filename = TransformUtil.mainName(tb);
			include = TransformUtil.include(tb);
			qcname = CName.qualified(tb, true);
		}
	}

	public static void write(IPath root, Info info) throws IOException {
		File target = root.append("src").append(info.filename).toFile();
		FileUtil.writeTemplate(MAIN_CPP_TMPL, target, info.include, info.qcname);
	}
}
