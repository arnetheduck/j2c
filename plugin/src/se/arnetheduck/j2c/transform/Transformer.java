package se.arnetheduck.j2c.transform;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import se.arnetheduck.j2c.snippets.GetSetSnippet;
import se.arnetheduck.j2c.snippets.ReplaceInvocation;

public class Transformer {
	private final IJavaProject project;

	private final ASTParser parser = ASTParser.newParser(AST.JLS4);

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

		parser.setProject(project);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);

		snippets.add(new GetSetSnippet());
		snippets.add(new ReplaceInvocation());
	}

	Set<IPackageBinding> packages = new TreeSet<IPackageBinding>(
			new BindingComparator());
	Set<ITypeBinding> headers = new TreeSet<ITypeBinding>(
			new BindingComparator());
	Set<ITypeBinding> impls = new TreeSet<ITypeBinding>(new BindingComparator());
	Set<ITypeBinding> stubs = new TreeSet<ITypeBinding>(new BindingComparator());
	Set<ITypeBinding> natives = new TreeSet<ITypeBinding>(
			new BindingComparator());
	private Set<ITypeBinding> hardDeps = new TreeSet<ITypeBinding>(
			new BindingComparator());
	private Set<ITypeBinding> softDeps = new TreeSet<ITypeBinding>(
			new BindingComparator());
	private Set<ITypeBinding> done = new TreeSet<ITypeBinding>(
			new BindingComparator());

	public List<Snippet> snippets = new ArrayList<Snippet>();

	Set<ITypeBinding> mains = new TreeSet<ITypeBinding>(new BindingComparator());

	public void process(IProgressMonitor monitor, ICompilationUnit... units)
			throws Exception {

		monitor.subTask("Moving old files");
		renameOld();

		write(monitor, units);

		processDeps(monitor);

		softDeps.addAll(headers);
		ForwardWriter fw = new ForwardWriter(root);
		fw.writeForward(packages, softDeps);

		for (ITypeBinding tb : mains) {
			MainWriter mw = new MainWriter(root, this, tb);
			mw.write();
		}

		MakefileWriter mw = new MakefileWriter(root);
		mw.write(name, impls, stubs, natives, mains);

		monitor.done();
		System.out.println("Done.");
	}

	private void renameOld() throws IOException {
		File r = root.toFile();
		File p = new File(System.getProperty("java.io.tmpdir"));

		File from = new File(p, r.getName() + 5);
		if (from.exists()) {
			for (File f : from.listFiles()) {
				f.delete();
			}
		}

		for (int i = 4; i > 0; --i) {
			from = new File(p, r.getName() + i);
			File to = new File(p, r.getName() + (i + 1));
			moveFiles(from, to);
		}

		moveFiles(r, new File(p, r.getName() + 1));
	}

	private void moveFiles(File from, File to) throws IOException {
		if (from.exists()) {
			to.mkdir();

			for (File f : from.listFiles()) {
				if (f.getName().endsWith(".o") || f.getName().endsWith(".a")) {
					f.delete();
				} else if (f.getName().equals("Makefile")
						|| f.getName().endsWith(".h")
						|| f.getName().endsWith(".cpp")) {
					File tf = new File(to, f.getName());

					if (!f.renameTo(tf)) {
						copyFile(f, tf);
						f.delete();
					}
				}
			}
		}
	}

	private static void copyFile(File sourceFile, File destFile)
			throws IOException {
		if (!destFile.exists()) {
			destFile.createNewFile();
		}

		FileChannel source = null;
		FileChannel destination = null;

		try {
			source = new FileInputStream(sourceFile).getChannel();
			destination = new FileOutputStream(destFile).getChannel();
			destination.transferFrom(source, 0, source.size());
		} finally {
			if (source != null) {
				source.close();
			}
			if (destination != null) {
				destination.close();
			}
		}
	}

	private void write(final IProgressMonitor monitor,
			ICompilationUnit... units) {

		for (ICompilationUnit[] u : split(units, 1024)) {
			parse(units, new ASTRequestor() {
				@Override
				public void acceptAST(ICompilationUnit source,
						CompilationUnit ast) {
					try {
						if (hasError(ast)) {
							for (AbstractTypeDeclaration type : (List<AbstractTypeDeclaration>) ast
									.types()) {
								ITypeBinding tb = type.resolveBinding();
								writeHeader(root, tb);
							}
						} else {
							writeImpl(monitor, root, ast);
						}
					} catch (Throwable e) {
						e.printStackTrace();
					}
				}
			});
		}
	}

	private static ICompilationUnit[][] split(ICompilationUnit[] units,
			int chunksize) {
		ICompilationUnit[][] ret = new ICompilationUnit[(int) Math
				.ceil(units.length / (double) chunksize)][];

		int start = 0;

		for (int i = 0; i < ret.length; i++) {
			ret[i] = Arrays.copyOfRange(units, start,
					Math.min(units.length, start + chunksize));
			start += chunksize;
		}

		return ret;
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
		parser.setProject(project);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);

		String bogusKey = BindingKey.createTypeBindingKey("java.lang.Object"); //$NON-NLS-1$
		String[] keys = new String[] { bogusKey }; // We need at least one here

		parser.createASTs(units, keys, requestor, null);
	}

	private void writeHeader(IPath root, ITypeBinding tb) throws Exception {
		done.add(tb);
		TypeBindingHeaderWriter hw = new TypeBindingHeaderWriter(root, this, tb);
		hw.write();
	}

	private void writeImpl(IProgressMonitor monitor, IPath root,
			CompilationUnit cu) throws Exception {
		monitor.subTask("Processing "
				+ cu.getJavaElement().getResource().getProjectRelativePath()
						.toString() + " (" + done.size() + " done, "
				+ hardDeps.size() + " dependencies pending)");
		UnitInfo ui = new UnitInfo();
		cu.accept(ui);

		for (AbstractTypeDeclaration type : (Iterable<AbstractTypeDeclaration>) cu
				.types()) {
			if (type instanceof TypeDeclaration) {
				TypeDeclaration td = (TypeDeclaration) type;
				ImplWriter iw = new ImplWriter(root, this,
						type.resolveBinding(), ui);

				iw.write(td);
			} else if (type instanceof AnnotationTypeDeclaration) {
				AnnotationTypeDeclaration td = (AnnotationTypeDeclaration) type;
				ImplWriter iw = new ImplWriter(root, this,
						type.resolveBinding(), ui);

				iw.write(td);
			} else if (type instanceof EnumDeclaration) {
				EnumDeclaration td = (EnumDeclaration) type;

				ImplWriter iw = new ImplWriter(root, this,
						type.resolveBinding(), ui);

				iw.write(td);
			}
		}

		done.addAll(ui.types);
	}

	public void processDeps(IProgressMonitor monitor) {
		while (!hardDeps.isEmpty()) {
			softDeps.addAll(hardDeps);

			Set<ICompilationUnit> units = new TreeSet<ICompilationUnit>(
					new ICUComparator());
			final List<ITypeBinding> arrays = new ArrayList<ITypeBinding>();
			final List<ITypeBinding> bindings = new ArrayList<ITypeBinding>();

			for (ITypeBinding tb : hardDeps) {
				if (done.contains(tb)) {
					continue;
				}

				done.add(tb);

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
							units.add(type.getCompilationUnit());
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			hardDeps.clear();

			for (ITypeBinding tb : arrays) {
				try {
					ArrayWriter aw = new ArrayWriter(root, this, tb);
					aw.write();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			for (ITypeBinding tb : bindings) {
				try {
					writeHeader(root, tb);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			if (!units.isEmpty()) {
				write(monitor, units.toArray(new ICompilationUnit[0]));
			}
		}
	}

	void hardDep(ITypeBinding dep) {
		if (!done.contains(dep)) {
			TransformUtil.addDep(dep, hardDeps);
		}
	}

	void softDep(ITypeBinding dep) {
		TransformUtil.addDep(dep, softDeps);
	}

	ITypeBinding resolve(Class<?> clazz) {
		try {
			parser.setProject(project);
			parser.setKind(ASTParser.K_COMPILATION_UNIT);
			parser.setResolveBindings(true);
			return (ITypeBinding) parser.createBindings(
					new IJavaElement[] { project.findType(clazz.getName()) },
					null)[0];
		} catch (JavaModelException e) {
			throw new Error(e);
		}
	}
}
