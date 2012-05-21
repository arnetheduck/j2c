package se.arnetheduck.j2c.transform;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.runtime.IPath;
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
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import se.arnetheduck.j2c.snippets.GetSetSnippet;

public class Transformer {
	private final IJavaProject project;

	private final ASTParser parser = ASTParser.newParser(AST.JLS4);

	private final String name;

	public final static class BindingComparator implements Comparator<IBinding> {
		@Override
		public int compare(IBinding o1, IBinding o2) {
			return o1.getKey().compareTo(o2.getKey());
		}
	}

	public final static class ICUComparator implements
			Comparator<ICompilationUnit> {
		@Override
		public int compare(ICompilationUnit o1, ICompilationUnit o2) {
			return o1.getPath().toString().compareTo(o2.getPath().toString());
		}
	}

	public Transformer(IJavaProject project, String name) throws Exception {
		this.project = project;
		this.name = name;

		parser.setProject(project);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);

		snippets.add(new GetSetSnippet());
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

	public void process(final IPath root, ICompilationUnit... units)
			throws Exception {
		File[] files = root.toFile().listFiles();

		for (File f : files) {
			if (f.getName().endsWith(".h") || f.getName().endsWith(".cpp")
					|| f.getName().endsWith(".o")) {
				f.delete();
			}
		}

		write(root, units);

		processDeps(root);

		softDeps.addAll(headers);
		ForwardWriter fw = new ForwardWriter(root);
		fw.writeForward(packages, softDeps);

		for (ITypeBinding tb : mains) {
			MainWriter mw = new MainWriter(root, this, tb);
			mw.write();
		}

		MakefileWriter mw = new MakefileWriter(root);
		mw.write(name, impls, stubs, natives, mains);
		System.out.println("Done.");
	}

	private void write(final IPath root, ICompilationUnit... units) {
		parse(units, new ASTRequestor() {
			@Override
			public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
				try {
					if (hasError(ast)) {
						for (AbstractTypeDeclaration type : (List<AbstractTypeDeclaration>) ast
								.types()) {
							ITypeBinding tb = type.resolveBinding();
							if (tb.isClass() || tb.isInterface() || tb.isEnum()) {
								writeHeader(root, tb);
							}
						}
					} else {
						writeImpl(root, ast);
					}
				} catch (Throwable e) {
					e.printStackTrace();
				}
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

	private List<CompilationUnit> parse(ICompilationUnit[] units,
			ASTRequestor requestor) {
		System.out.println("Processing " + units.length + " units");
		parser.setProject(project);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);

		final List<CompilationUnit> ret = new ArrayList<CompilationUnit>();
		String bogusKey = BindingKey.createTypeBindingKey("java.lang.Object"); //$NON-NLS-1$
		String[] keys = new String[] { bogusKey }; // We need at least one here

		parser.createASTs(units, keys, requestor, null);
		return ret;
	}

	private void writeHeader(IPath root, ITypeBinding tb) throws Exception {
		done.add(tb);
		TypeBindingHeaderWriter hw = new TypeBindingHeaderWriter(root, this, tb);
		hw.write();
	}

	private void writeImpl(IPath root, CompilationUnit cu) throws Exception {
		UnitInfo ui = new UnitInfo();
		cu.accept(ui);

		for (AbstractTypeDeclaration type : (Iterable<AbstractTypeDeclaration>) cu
				.types()) {
			if (type instanceof TypeDeclaration) {
				TypeDeclaration td = (TypeDeclaration) type;
				ImplWriter iw = new ImplWriter(root, this,
						type.resolveBinding(), ui);

				iw.write(td);
				HeaderWriter hw = new HeaderWriter(root, this,
						type.resolveBinding(), ui);
				hw.write(td);
			} else if (type instanceof EnumDeclaration) {
				EnumDeclaration td = (EnumDeclaration) type;

				ImplWriter iw = new ImplWriter(root, this,
						type.resolveBinding(), ui);

				iw.write(td);
				HeaderWriter hw = new HeaderWriter(root, this,
						type.resolveBinding(), ui);
				hw.write(td);
				done.add(iw.type);
			}
		}

		done.addAll(ui.types);
	}

	public void processDeps(IPath root) {
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
				write(root, units.toArray(new ICompilationUnit[0]));
			}
		}
	}

	void hardDep(ITypeBinding dep) {
		TransformUtil.addDep(dep, hardDeps);
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
