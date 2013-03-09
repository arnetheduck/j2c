package se.arnetheduck.j2c.transform;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
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

	private final Header header;
	private String access;

	public HeaderWriter(IPath root, Transformer ctx, UnitInfo unitInfo,
			TypeInfo typeInfo) {
		super(ctx, unitInfo, typeInfo);
		this.root = root;

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

			header.write(root, body, typeInfo.closures(), typeInfo.hasClinit(),
					typeInfo.hasInit(), unitInfo.types, access);
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
		TransformUtil.printSignature(ctx, out, type, node.resolveBinding(),
				deps, false);
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

		int modifiers = node.getModifiers();
		if (TransformWriter.isAnySpecial(fragments)) {
			for (VariableDeclarationFragment f : fragments) {
				IVariableBinding vb = f.resolveBinding();
				boolean asMethod = TransformUtil.asMethod(vb);
				access = Header.printAccess(out, asMethod ? Modifier.PRIVATE
						: vb.getModifiers(), access);

				Object cv = TransformUtil.constexprValue(f);
				printi(TransformUtil.fieldModifiers(type, modifiers, true,
						cv != null));

				print(TransformUtil.varTypeCName(modifiers, vb.getType(), type,
						deps) + " ");

				f.accept(this);

				println(asMethod ? "_;" : ";");
			}
		} else {
			access = Header.printAccess(out, modifiers, access);

			printi(TransformUtil.fieldModifiers(type, modifiers, true, false));

			ITypeBinding tb = node.getType().resolveBinding();
			print(TransformUtil.varTypeCName(modifiers, tb, type, deps));

			print(" ");

			visitAllCSV(fragments, false);

			println(";");
		}

		return false;
	}

	@Override
	public boolean visit(Initializer node) {
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
		if (TransformUtil.baseDeclared(ctx, type, mb)) {
			// Defining once more will lead to virtual inheritance issues
			printi("/*");
			TransformUtil.printSignature(ctx, out, type, mb, deps, false);
			println("; (already declared) */");
			return false;
		}

		header.method(mb);

		if (node.isConstructor()) {
			access = Header.printProtected(out, access);

			printi("void " + CName.CTOR + "(");
			String sep = TransformUtil.printEnumCtorParams(ctx, out, type, "",
					deps);
			if (!node.parameters().isEmpty()) {
				print(sep);
			}
		} else {
			access = Header.printAccess(out, mb, access);

			printi(TransformUtil.methodModifiers(mb));
			print(TransformUtil.typeParameters(node.typeParameters()));

			ITypeBinding rt = TransformUtil.returnType(type, node);
			softDep(rt);
			print(TransformUtil.relativeRef(rt, type, true) + " ");

			node.getName().accept(this);
			print("(");

			for (ITypeBinding rd : TransformUtil.returnDeps(type, mb,
					ctx.resolve(Object.class))) {
				hardDep(rd);
			}
		}

		visitAllCSV(node.parameters(), false);

		print(")");

		print(TransformUtil.throwsDecl(node.thrownExceptions()));

		print(TransformUtil.methodSpecifiers(mb));

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
			print(TransformUtil.relativeRef(tb, type, true));
			print("/*...*/");
		} else {
			print(TransformUtil.varTypeCName(node.getModifiers(), tb, type,
					deps));
		}

		print(" ");

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

		node.getName().accept(this);

		String iv = TransformUtil.initialValue(vb);
		if (iv != null) {
			print(" { " + iv + " }");
		}

		return false;
	}
}
