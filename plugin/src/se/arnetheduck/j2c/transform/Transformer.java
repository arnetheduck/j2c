package se.arnetheduck.j2c.transform;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.BindingKey;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import se.arnetheduck.j2c.snippets.GetSetSnippet;
import se.arnetheduck.j2c.snippets.ReplaceInvocation;

public class Transformer {
	private final IJavaProject project;

	private final String name;

	private final IPath root;

	public final static class ICUComparator implements
			Comparator<ICompilationUnit> {
		@Override
		public int compare(ICompilationUnit o1, ICompilationUnit o2) {
			return o1.getPath().toString().compareTo(o2.getPath().toString());
		}
	}

	public Transformer(IJavaProject project, String name, IPath root)
			throws Exception {
		this.project = project;
		this.name = name;
		this.root = root;

		snippets.add(new GetSetSnippet());
		snippets.add(new ReplaceInvocation());
	}

	public final Set<ICompilationUnit> selection = new TreeSet<ICompilationUnit>(
			new ICUComparator());

	/** Compilation units that are scheduled for processing */
	public final Set<ICompilationUnit> todo = new TreeSet<ICompilationUnit>(
			new ICUComparator());

	/** Type bindings that didn't have a corresponding compilation unit */
	private Set<ITypeBinding> hardDeps = new TreeSet<ITypeBinding>(
			new BindingComparator());

	private final Map<String, ForwardWriter.Info> forwards = new HashMap<String, ForwardWriter.Info>();

	private final Map<String, MainWriter.Info> mains = new TreeMap<String, MainWriter.Info>();

	private final MakefileWriter.Info sel = new MakefileWriter.Info();
	private final MakefileWriter.Info ext = new MakefileWriter.Info();

	private MakefileWriter.Info cur;

	private final Set<String> done = new HashSet<String>();

	public List<Snippet> snippets = new ArrayList<Snippet>();

	protected AST currentAST;

	public void process(IProgressMonitor monitor, ICompilationUnit... units)
			throws Exception {
		monitor.subTask("Moving old files");
		renameOld();

		hardDep(resolve(ClassLoader.class));
		selection.addAll(Arrays.asList(units));
		todo.addAll(selection);

		while (!todo.isEmpty()) {
			processSome(monitor);
		}

		new ForwardWriter(root).write(forwards.values());

		for (MainWriter.Info main : mains.values()) {
			MainWriter.write(root, main);
			sel.mains.add("src/" + main.filename);
		}

		MakefileWriter mw = new MakefileWriter(root);
		mw.write(name, sel, ext);

		monitor.done();
		System.out.println("Done.");
	}

	private void renameOld() throws IOException {
		File r = root.toFile();
		File p = new File(System.getProperty("java.io.tmpdir"));

		File from = new File(p, r.getName() + 5);
		if (from.exists()) {
			clear(from);
		}

		for (int i = 4; i > 0; --i) {
			from = new File(p, r.getName() + i);
			File to = new File(p, r.getName() + (i + 1));
			moveFiles(from, to);
		}

		moveFiles(r, new File(p, r.getName() + 1));
	}

	private void clear(File from) {
		for (File f : from.listFiles()) {
			if (f.isDirectory()) {
				clear(f);
			}

			f.delete();
		}
	}

	private void moveFiles(File from, File to) throws IOException {
		if (from.exists()) {
			to.mkdir();

			for (File f : from.listFiles()) {
				if (f.isDirectory()) {
					moveFiles(f, new File(to, f.getName()));
					f.delete(); // Will only delete empty folders
				} else if (f.getName().endsWith(".o")
						|| f.getName().endsWith(".a")) {
					f.delete();
				} else if (f.getName().equals("Makefile")
						|| f.getName().endsWith(".hpp")
						|| f.getName().endsWith(".cpp")) {
					File tf = new File(to, f.getName());

					if (!f.renameTo(tf)) {
						Files.copy(f.toPath(), tf.toPath());
						f.delete();
					}
				}
			}
		}
	}

	private void processSome(IProgressMonitor monitor) {
		ICompilationUnit[] units = new ICompilationUnit[Math.min(256,
				todo.size())];
		Iterator<ICompilationUnit> it = todo.iterator();
		for (int n = 0; it.hasNext() && n < units.length; n++) {
			units[n] = it.next();
			it.remove();
		}

		write(monitor, units);
		writeDeps(monitor);
	}

	private void write(final IProgressMonitor monitor,
			ICompilationUnit... units) {
		parse(units, new ASTRequestor() {
			@Override
			public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
				currentAST = ast.getAST();
				if (selection.contains(source)) {
					cur = sel;
				} else {
					cur = ext;
				}

				try {
					if (hasError(ast)) {
						for (AbstractTypeDeclaration type : (List<AbstractTypeDeclaration>) ast
								.types()) {
							ITypeBinding tb = type.resolveBinding();
							writeHeader(source, tb);
						}
					} else {
						writeImpl(monitor, source, ast);
					}
				} catch (Throwable e) {
					e.printStackTrace();
				}
				currentAST = null;
			}
		});
	}

	private static boolean hasError(CompilationUnit ast) {
		for (IProblem p : ast.getProblems()) {
			if (p.isError()) {
				return true;
			}
		}

		return false;
	}

	private void parse(ICompilationUnit[] units, ASTRequestor requestor) {
		System.out.println("Processing " + units.length + " units");
		ASTParser parser = ASTParser.newParser(AST.JLS4);
		parser.setProject(project);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);

		String bogusKey = BindingKey.createTypeBindingKey("java.lang.Object"); //$NON-NLS-1$
		String[] keys = new String[] { bogusKey }; // We need at least one here

		parser.createASTs(units, keys, requestor, null);
	}

	private void writeHeader(ICompilationUnit unit, ITypeBinding tb)
			throws Exception {
		done.add(tb.getBinaryName());
		TypeBindingHeaderWriter hw = new TypeBindingHeaderWriter(getRoot(unit),
				this, tb);
		hw.write();
	}

	private void writeImpl(IProgressMonitor monitor, ICompilationUnit unit,
			CompilationUnit cu) throws Exception {
		monitor.subTask("Processing "
				+ cu.getJavaElement().getResource().getProjectRelativePath()
						.toString() + " (" + done.size() + " types done, "
				+ todo.size() + " units and " + hardDeps.size()
				+ " dependencies pending)");
		UnitInfo ui = new UnitInfo();
		cu.accept(ui);

		for (AbstractTypeDeclaration type : (Iterable<AbstractTypeDeclaration>) cu
				.types()) {
			if (type instanceof TypeDeclaration) {
				TypeDeclaration td = (TypeDeclaration) type;
				ImplWriter iw = new ImplWriter(getRoot(unit), this,
						type.resolveBinding(), ui);

				iw.write(td);
			} else if (type instanceof AnnotationTypeDeclaration) {
				AnnotationTypeDeclaration td = (AnnotationTypeDeclaration) type;
				ImplWriter iw = new ImplWriter(getRoot(unit), this,
						type.resolveBinding(), ui);

				iw.write(td);
			} else if (type instanceof EnumDeclaration) {
				EnumDeclaration td = (EnumDeclaration) type;

				ImplWriter iw = new ImplWriter(getRoot(unit), this,
						type.resolveBinding(), ui);

				iw.write(td);
			}
		}

		for (ITypeBinding tb : ui.types) {
			done.add(tb.getBinaryName());
		}
	}

	private void writeDeps(IProgressMonitor monitor) {
		final Set<ITypeBinding> arrays = new TreeSet<ITypeBinding>(
				new BindingComparator());
		while (!hardDeps.isEmpty()) {
			final List<ITypeBinding> bindings = new ArrayList<ITypeBinding>();

			cur = ext;

			for (ITypeBinding tb : hardDeps) {
				if (done.contains(tb.getBinaryName())) {
					continue;
				}

				done.add(tb.getBinaryName());

				if (tb.isPrimitive()) {
					continue;
				}

				try {
					if (tb.isArray()) {
						arrays.add(tb);
					} else {
						IJavaElement elem = project.findElement(tb.getKey(),
								null);
						IType type = elem instanceof IType ? (IType) elem
								: null;
						if (type == null || type.getCompilationUnit() == null) {
							bindings.add(tb);
						} else {
							todo.add(type.getCompilationUnit());
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			hardDeps.clear();

			for (ITypeBinding tb : bindings) {
				try {
					writeHeader(null, tb);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			cur = sel;
			for (ITypeBinding tb : arrays) {
				try {
					ArrayWriter aw = new ArrayWriter(root, this, tb);
					aw.write();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	private IPath getRoot(ICompilationUnit unit) {
		if (unit != null && selection.contains(unit)) {
			return root;
		}

		return root.append("ext");
	}

	public void hardDep(ITypeBinding dep) {
		if (dep != null && !done.contains(dep.getBinaryName())) {
			TransformUtil.addDep(dep, hardDeps);
		}
	}

	public void softDep(ITypeBinding dep) {
		if (dep != null) {
			ForwardWriter.Info info = new ForwardWriter.Info(dep);
			forwards.put(dep.getBinaryName(), info);
		}
	}

	public void main(ITypeBinding tb) {
		MainWriter.Info info = new MainWriter.Info(tb);
		mains.put(info.qcname, info);
	}

	private final Map<String, ITypeBinding> bindings = new WeakHashMap<String, ITypeBinding>();

	public ITypeBinding resolve(Class<?> clazz) {
		ITypeBinding ret;
		String name = clazz.getName();
		if (currentAST != null) {
			ret = currentAST.resolveWellKnownType(name);
			if (ret != null) {
				return ret;
			}
		}

		ret = bindings.get(name);
		if (ret != null) {
			return ret;
		}

		try {
			ASTParser parser = ASTParser.newParser(AST.JLS4);
			parser.setProject(project);
			parser.setKind(ASTParser.K_COMPILATION_UNIT);
			parser.setResolveBindings(true);
			ret = (ITypeBinding) parser.createBindings(
					new IJavaElement[] { project.findType(name) }, null)[0];
			bindings.put(name, ret);
			return ret;
		} catch (JavaModelException e) {
			throw new Error(e);
		}
	}

	public void addImpl(ITypeBinding tb) {
		cur.impls.add(TransformUtil.implPath(root, tb, "").makeRelativeTo(root)
				.toString());
	}

	public void addNative(ITypeBinding tb) {
		cur.natives.add(TransformUtil.implPath(root, tb, TransformUtil.NATIVE)
				.makeRelativeTo(root).toString());
	}

	public void addStub(ITypeBinding tb) {
		cur.stubs.add(TransformUtil.implPath(root, tb, TransformUtil.STUB)
				.makeRelativeTo(root).toString());
	}
}
