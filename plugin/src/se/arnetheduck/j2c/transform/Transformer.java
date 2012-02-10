package se.arnetheduck.j2c.transform;

import java.io.File;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
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
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;

public class Transformer {
	private final IJavaProject project;

	private final ASTParser parser = ASTParser.newParser(AST.JLS4);

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

	public Transformer(IJavaProject project) throws Exception {
		this.project = project;

		parser.setProject(project);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
	}

	Set<IPackageBinding> packages = new TreeSet<IPackageBinding>(
			new PackageBindingComparator());
	Set<ITypeBinding> headers = new TreeSet<ITypeBinding>(
			new TypeBindingComparator());
	Set<ITypeBinding> impls = new TreeSet<ITypeBinding>(
			new TypeBindingComparator());
	private Set<ITypeBinding> hardDeps = new TreeSet<ITypeBinding>(
			new TypeBindingComparator());
	private Set<ITypeBinding> softDeps = new TreeSet<ITypeBinding>(
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
			writeImpl(root, cu);
		}

		processDeps(root);

		softDeps.addAll(headers);
		ForwardWriter fw = new ForwardWriter(root);
		fw.writeForward(packages, softDeps);
		fw.writePackageHeaders(headers);

		MakefileWriter mw = new MakefileWriter(root);
		mw.write(impls);
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

	private void writeHeader(IPath root, CompilationUnit cu) throws Exception {
		for (AbstractTypeDeclaration type : (Iterable<AbstractTypeDeclaration>) cu
				.types()) {
			if (type instanceof TypeDeclaration) {
				HeaderWriter hw = new HeaderWriter(root, this,
						type.resolveBinding());
				hw.write((TypeDeclaration) type, new HashSet<ITypeBinding>());
			}
		}
	}

	private void writeHeader(IPath root, ITypeBinding tb) throws Exception {
		TypeBindingHeaderWriter hw = new TypeBindingHeaderWriter(root, this, tb);
		hw.write();

		headers.addAll(hw.getTypes());
		hardDeps.addAll(hw.getHardDeps());
		softDeps.addAll(hw.getSoftDeps());
	}

	private void writeImpl(IPath root, CompilationUnit cu) throws Exception {
		for (AbstractTypeDeclaration type : (Iterable<AbstractTypeDeclaration>) cu
				.types()) {
			if (type instanceof TypeDeclaration) {
				ImplWriter iw = new ImplWriter(root, this,
						type.resolveBinding(), cu.imports());

				iw.write((TypeDeclaration) type);
				HeaderWriter hw = new HeaderWriter(root, this,
						type.resolveBinding());
				hw.write((TypeDeclaration) type, iw.nestedTypes);

			}
		}
	}

	public void processDeps(IPath root) throws Exception {
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

	private void writeDep(IPath root, ITypeBinding tb) throws Exception {
		if (headers.contains(tb)) {
			return;
		}

		if (tb.isArray()) {
			ArrayWriter aw = new ArrayWriter(root, this, tb);
			aw.write();
			headers.add(tb);
			hardDep(aw.getSuperType());

			return;
		}

		IType type = project.findType(tb.getQualifiedName());
		if (type == null || type.getCompilationUnit() == null) {
			writeHeader(root, tb);
			return;
		}

		writeHeader(root, parse(type.getCompilationUnit()));
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
