package se.arnetheduck.j2c.transform;

import java.io.File;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
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

	private ITypeBinding object;

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
		object = (ITypeBinding) parser
				.createBindings(new IJavaElement[] { project
						.findType(Object.class.getName()) }, null)[0];
	}

	private Set<IPackageBinding> packages = new TreeSet<IPackageBinding>(
			new PackageBindingComparator());
	private Set<ITypeBinding> headers = new TreeSet<ITypeBinding>(
			new TypeBindingComparator());
	private Set<ITypeBinding> impls = new TreeSet<ITypeBinding>(
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
			writeHeader(root, cu);
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
				HeaderWriter hw = new HeaderWriter(root, type.resolveBinding());
				hw.write((TypeDeclaration) type);

				packages.addAll(hw.getPackages());
				headers.addAll(hw.getTypes());
				hardDeps.addAll(hw.getHardDeps());
				softDeps.addAll(hw.getSoftDeps());
			}
		}
	}

	private void writeHeader(IPath root, ITypeBinding tb) throws Exception {
		TypeBindingHeaderWriter hw = new TypeBindingHeaderWriter(object, root);
		hw.write(tb);

		headers.addAll(hw.getTypes());
		hardDeps.addAll(hw.getHardDeps());
		softDeps.addAll(hw.getSoftDeps());
	}

	private void writeImpl(IPath root, CompilationUnit cu) throws Exception {
		for (AbstractTypeDeclaration type : (Iterable<AbstractTypeDeclaration>) cu
				.types()) {
			if (type instanceof TypeDeclaration) {
				ImplWriter iw = new ImplWriter(root, type.resolveBinding(),
						cu.imports());

				iw.write((TypeDeclaration) type);

				packages.addAll(iw.getPackages());
				headers.addAll(iw.getTypes());
				impls.addAll(iw.getTypes());
				hardDeps.addAll(iw.getHardDeps());
				softDeps.addAll(iw.getSoftDeps());
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
			ArrayWriter aw = new ArrayWriter(root);
			aw.write(tb);
			return;
		}

		IType type = project.findType(tb.getQualifiedName());
		if (type == null || type.getCompilationUnit() == null) {
			writeHeader(root, tb);
			return;
		}

		writeHeader(root, parse(type.getCompilationUnit()));
	}
}
