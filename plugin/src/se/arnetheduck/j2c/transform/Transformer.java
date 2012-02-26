package se.arnetheduck.j2c.transform;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import se.arnetheduck.j2c.snippets.GetSetSnippet;

public class Transformer {
	private final IJavaProject project;

	private final ASTParser parser = ASTParser.newParser(AST.JLS4);

	private final String name;

	public final static class PackageBindingComparator implements
			Comparator<IPackageBinding> {
		@Override
		public int compare(IPackageBinding o1, IPackageBinding o2) {
			return o1.getKey().compareTo(o2.getKey());
		}
	}

	public final static class TypeBindingComparator implements
			Comparator<ITypeBinding> {
		@Override
		public int compare(ITypeBinding o1, ITypeBinding o2) {
			return o1.getKey().compareTo(o2.getKey());
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
			new PackageBindingComparator());
	Set<ITypeBinding> headers = new TreeSet<ITypeBinding>(
			new TypeBindingComparator());
	Set<ITypeBinding> impls = new TreeSet<ITypeBinding>(
			new TypeBindingComparator());
	Set<ITypeBinding> stubs = new TreeSet<ITypeBinding>(
			new TypeBindingComparator());
	private Set<ITypeBinding> hardDeps = new TreeSet<ITypeBinding>(
			new TypeBindingComparator());
	private Set<ITypeBinding> softDeps = new TreeSet<ITypeBinding>(
			new TypeBindingComparator());

	public List<Snippet> snippets = new ArrayList<Snippet>();

	Set<ITypeBinding> mains = new TreeSet<ITypeBinding>(
			new TypeBindingComparator());

	public void process(IPath root, ICompilationUnit... units) throws Exception {
		File[] files = root.toFile().listFiles();

		for (File f : files) {
			if (f.getName().endsWith(".h") || f.getName().endsWith(".cpp")
					|| f.getName().endsWith(".o")) {
				f.delete();
			}
		}

		for (ICompilationUnit unit : units) {
			CompilationUnit cu = parse(unit);
			try {
				writeImpl(root, cu);
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}

		processDeps(root);

		softDeps.addAll(headers);
		ForwardWriter fw = new ForwardWriter(root);
		fw.writeForward(packages, softDeps);
		fw.writePackageHeaders(headers);

		for (ITypeBinding tb : mains) {
			MainWriter mw = new MainWriter(root, this, tb);
			mw.write();
		}

		MakefileWriter mw = new MakefileWriter(root);
		mw.write(name, impls, stubs, mains);
		System.out.println("Done.");
	}

	private CompilationUnit parse(ICompilationUnit unit) {
		System.out.println("Processing " + unit.getPath());
		parser.setProject(project);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setSource(unit);
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		return cu;
	}

	private void writeHeader(IPath root, ITypeBinding tb) throws Exception {
		TypeBindingHeaderWriter hw = new TypeBindingHeaderWriter(root, this, tb);
		hw.write();
	}

	private void writeImpl(IPath root, CompilationUnit cu) throws Exception {
		for (AbstractTypeDeclaration type : (Iterable<AbstractTypeDeclaration>) cu
				.types()) {
			if (type instanceof TypeDeclaration) {
				TypeDeclaration td = (TypeDeclaration) type;
				if (td.isInterface()) {
					HeaderWriter hw = new HeaderWriter(root, this,
							type.resolveBinding());
					hw.write(td, new ArrayList<ITypeBinding>());
					continue;
				}

				ImplWriter iw = new ImplWriter(root, this,
						type.resolveBinding(), cu.imports());

				iw.write(td);
				HeaderWriter hw = new HeaderWriter(root, this,
						type.resolveBinding());
				hw.write(td, iw.nestedTypes);
			} else if (type instanceof EnumDeclaration) {
				EnumDeclaration td = (EnumDeclaration) type;

				ImplWriter iw = new ImplWriter(root, this,
						type.resolveBinding(), cu.imports());

				iw.write(td);
				HeaderWriter hw = new HeaderWriter(root, this,
						type.resolveBinding());
				hw.write(td, iw.nestedTypes);
			}
		}
	}

	public void processDeps(IPath root) {
		while (!hardDeps.isEmpty()) {
			Iterator<ITypeBinding> it = hardDeps.iterator();
			ITypeBinding tb = it.next();
			softDeps.add(tb);
			if (tb.getPackage() != null) {
				packages.add(tb.getPackage());
			}
			it.remove();
			writeDep(root, tb);
		}
	}

	private void writeDep(IPath root, ITypeBinding tb) {
		try {
			if (!headers.contains(tb)) {
				if (tb.isArray()) {
					ArrayWriter aw = new ArrayWriter(root, this, tb);
					aw.write();
					hardDep(aw.getSuperType());

					return;
				}

				IType type = project.findType(tb.getQualifiedName());
				if (type == null || type.getCompilationUnit() == null) {
					writeHeader(root, tb);
					return;

				}

				CompilationUnit cu = parse(type.getCompilationUnit());
				writeImpl(root, cu);
			}
		} catch (Exception e) {
			e.printStackTrace();
			headers.add(tb); // To avoid endless loops
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
