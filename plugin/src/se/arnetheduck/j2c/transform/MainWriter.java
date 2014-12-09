package se.arnetheduck.j2c.transform;

import java.io.File;
import java.io.IOException;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.ITypeBinding;

public class MainWriter {
	private static final String MAIN_CPP_TMPL = "/se/arnetheduck/j2c/resources/Main.cpp.tmpl";

	public static String write(IPath root, ITypeBinding tb) throws IOException {
		String filename = TransformUtil.mainName(tb);
		String include = TransformUtil.include(tb);
		String qcname = CName.qualified(tb, true);

		File target = root.append("src").append(filename).toFile();
		FileUtil.writeTemplate(MAIN_CPP_TMPL, target, include, qcname);
		return filename;
	}
}
