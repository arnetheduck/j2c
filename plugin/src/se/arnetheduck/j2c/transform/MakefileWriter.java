package se.arnetheduck.j2c.transform;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.runtime.IPath;

public class MakefileWriter {
	private static final String MAKEFILE_TMPL = "/se/arnetheduck/j2c/resources/Makefile.tmpl";

	public static class Info {
		public final Set<String> impls = new TreeSet<String>();
		public final Set<String> stubs = new TreeSet<String>();
		public final Set<String> natives = new TreeSet<String>();
		public final Set<String> mains = new TreeSet<String>();
	}

	private final IPath root;

	public MakefileWriter(IPath root) {
		this.root = root;
	}

	public void write(String name, Info sel, Info ext) throws IOException {
		FileUtil.writeTemplate(MAKEFILE_TMPL, root.append("Makefile").toFile(),
				name, list(sel.impls, ""), list(sel.stubs, ""),
				list(sel.natives, ""), list(ext.impls, "ext/"),
				list(ext.stubs, "ext/"), list(ext.natives, "ext/"),
				list(sel.mains, ""));
	}

	private static String list(Collection<String> items, String prefix) {
		StringBuilder sb = new StringBuilder();
		for (String item : items) {
			sb.append("\t" + prefix + item + " \\\n");
		}

		return sb.toString();

	}
}
