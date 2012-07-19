package se.arnetheduck.j2c.transform;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

public class HeaderWriter extends TransformWriter {
	private final IPath root;
	private final Collection<IVariableBinding> closures;

	private final Header header;
	private String access;

	private boolean hasInit;

	public HeaderWriter(IPath root, Transformer ctx, ITypeBinding type,
			UnitInfo unitInfo, Collection<IVariableBinding> closures) {
		super(ctx, type, unitInfo);
		this.root = root;
		this.closures = closures;

		access = Header.initialAccess(type);

		header = new Header(ctx, type, deps);
	}

	public void write(AnnotationTypeDeclaration node) throws Exception {
		writeType(node.bodyDeclarations());
	}

	public void write(AnonymousClassDeclaration node) throws Exception {
		writeType(node.bodyDeclarations());
	}

	public void write(EnumDeclaration node) throws Exception {
		writeType(node.enumConstants(), node.bodyDeclarations());
	}

	public void write(TypeDeclaration node) throws Exception {
		writeType(node.bodyDeclarations());
	}

	private void writeType(List<BodyDeclaration> declarations) {
		writeType(new ArrayList<EnumConstantDeclaration>(), declarations);
	}

	private void writeType(List<EnumConstantDeclaration> enums,
			List<BodyDeclaration> declarations) {
		try {
			String body = getBody(enums, declarations);

			header.write(root, body, closures, hasInit, unitInfo.types, access);
		} catch (Exception e) {
			throw new Error(e);
		}
	}

	private String getBody(List<EnumConstantDeclaration> enums,
			List<BodyDeclaration> declarations) {
		StringWriter sw = new StringWriter();
		out = new PrintWriter(sw);

		indent++;

		visitAll(enums);

		visitAll(declarations); // This will gather constructors

		indent--;

		out.close();
		out = null;
		return sw.toString();
	}

	@Override
	public boolean preVisit2(ASTNode node) {
		for (Snippet snippet : ctx.snippets) {
			if (!snippet.node(ctx, this, node)) {
				return false;
			}
		}

		return super.preVisit2(node);
	}

	private List<Class<?>> handledBlocks = new ArrayList<Class<?>>(
			Arrays.asList(TypeDeclaration.class));

	@Override
	public boolean visit(AnnotationTypeDeclaration node) {
		return false;
	}

	@Override
	public boolean visit(AnnotationTypeMemberDeclaration node) {
		printi();
		TransformUtil.printSignature(out, type, node.resolveBinding(), deps,
				false);
		// TODO defaults
		println(" = 0;");
		return false;
	}

	@Override
	public boolean visit(Block node) {
		if (!handledBlocks.contains(node.getParent().getClass())) {
			printlni("{");

			indent++;

			for (Object o : node.statements()) {
				Statement s = (Statement) o;
				s.accept(this);
			}

			indent--;
			printlni("}");
			println();
		}

		return false;
	}

	@Override
	public boolean visit(EnumDeclaration node) {
		return false;
	}

	@Override
	public boolean visit(EnumConstantDeclaration node) {
		access = Header.printAccess(out, node.getModifiers(), access);
		printi("static " + CName.of(type) + " *");

		node.getName().accept(this);
		println(";");

		return false;
	}

	@Override
	public boolean visit(FieldDeclaration node) {
		if (node.getJavadoc() != null) {
			node.getJavadoc().accept(this);
		}

		List<VariableDeclarationFragment> fragments = node.fragments();

		ITypeBinding tb = node.getType().resolveBinding();
		if (isAnySpecial(fragments)) {
			for (VariableDeclarationFragment f : fragments) {
				IVariableBinding vb = f.resolveBinding();
				boolean asMethod = TransformUtil.asMethod(vb);
				access = Header.printAccess(out, asMethod ? Modifier.PRIVATE
						: vb.getModifiers(), access);

				printi(TransformUtil.fieldModifiers(type, node.getModifiers(),
						true, TransformUtil.constantValue(f) != null));

				print(CName.relative(vb.getType(), type, true) + " ");

				f.accept(this);

				println(asMethod ? "_;" : ";");
			}
		} else {
			access = Header.printAccess(out, node.getModifiers(), access);

			printi(TransformUtil.fieldModifiers(type, node.getModifiers(),
					true, hasInitilializer(fragments)));

			print(CName.relative(tb, type, true) + " ");

			visitAllCSV(fragments, false);

			println(";");
		}

		return false;
	}

	/**
	 * Fields that for some reason cannot be declared together (different C++
	 * type, implemented as methods, etc)
	 */
	private static boolean isAnySpecial(
			List<VariableDeclarationFragment> fragments) {
		for (VariableDeclarationFragment f : fragments) {
			if (f.getExtraDimensions() != 0) {
				return true;
			}

			if (TransformUtil.constantValue(f) != null) {
				return true;
			}

			if (TransformUtil.isStatic(f.resolveBinding())) {
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean visit(Initializer node) {
		if (!Modifier.isStatic(node.getModifiers())) {
			hasInit = true;
		}

		return false;
	}

	@Override
	public boolean visit(Javadoc node) {
		if (true)
			return false;
		printi("/** ");
		for (Iterator it = node.tags().iterator(); it.hasNext();) {
			ASTNode e = (ASTNode) it.next();
			e.accept(this);
		}
		println("\n */");
		return false;
	}

	@Override
	public boolean visit(MethodDeclaration node) {
		if (node.getJavadoc() != null) {
			node.getJavadoc().accept(this);
		}

		IMethodBinding mb = node.resolveBinding();
		if (Header.baseDeclared(ctx, type, mb)) {
			// Defining once more will lead to virtual inheritance issues
			printi("/*");
			TransformUtil.printSignature(out, type, mb, deps, false);
			println("; (already declared) */");
			return false;
		}

		header.method(mb);

		if (node.isConstructor()) {
			access = Header.printProtected(out, access);

			printi("void " + CName.CTOR);
		} else {
			access = Header.printAccess(out, mb, access);

			printi(TransformUtil.methodModifiers(mb));
			print(TransformUtil.typeParameters(node.typeParameters()));

			ITypeBinding rt = TransformUtil.returnType(node);
			softDep(rt);
			print(CName.relative(rt, type, true) + " " + TransformUtil.ref(rt));

			node.getName().accept(this);

			for (ITypeBinding rd : TransformUtil.returnDeps(type, mb,
					ctx.resolve(Object.class))) {
				hardDep(rd);
			}
		}

		visitAllCSV(node.parameters(), true);

		print(TransformUtil.throwsDecl(node.thrownExceptions()));

		if (node.getBody() == null && !Modifier.isNative(node.getModifiers())) {
			print(" = 0");
		}

		println(";");

		return false;
	}

	@Override
	public boolean visit(SimpleName node) {
		IBinding b = node.resolveBinding();
		if (b instanceof ITypeBinding) {
			softDep((ITypeBinding) b);
			print(CName.relative((ITypeBinding) b, type, false));
			return false;
		}
		return super.visit(node);
	}

	@Override
	public boolean visit(SimpleType node) {
		ITypeBinding b = node.resolveBinding();
		softDep(b);
		print(CName.relative(b, type, false));
		return false;
	}

	@Override
	public boolean visit(QualifiedName node) {
		IBinding b = node.resolveBinding();
		if (b instanceof ITypeBinding) {
			softDep((ITypeBinding) b);
			print(CName.relative((ITypeBinding) b, type, false));
			return false;
		}
		return super.visit(node);
	}

	@Override
	public boolean visit(QualifiedType node) {
		ITypeBinding b = node.resolveBinding();
		softDep(b);
		print(CName.relative(b, type, false));
		return false;
	}

	@Override
	public boolean visit(SingleVariableDeclaration node) {
		ITypeBinding tb = node.getType().resolveBinding();

		if (node.getExtraDimensions() > 0) {
			softDep(tb);
			tb = tb.createArrayType(node.getExtraDimensions());
		}

		if (node.isVarargs()) {
			tb = tb.createArrayType(1);
			print(CName.relative(tb, type, true));
			print("/*...*/");
		} else {
			print(CName.relative(tb, type, true));
		}

		print(" " + TransformUtil.ref(tb));

		node.getName().accept(this);

		return false;
	}

	@Override
	public boolean visit(TypeDeclaration node) {
		return false;
	}

	@Override
	public boolean visit(VariableDeclarationFragment node) {
		IVariableBinding vb = node.resolveBinding();
		ITypeBinding tb = vb.getType();
		header.field(vb);
		softDep(tb);
		print(TransformUtil.ref(tb));

		node.getName().accept(this);
		Object v = TransformUtil.constantValue(node);
		if (v != null) {
			print(" = " + v);
		} else {
			if (node.getInitializer() != null) {
				if (!Modifier.isStatic(node.resolveBinding().getModifiers())) {
					hasInit = true;
				}
			}
		}

		return false;
	}
}
