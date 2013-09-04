package se.arnetheduck.j2c.transform;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

public class TypeInfoVisitor extends ShallowASTVisitor {
	private Transformer ctx;
	private TypeInfo typeInfo;

	public TypeInfoVisitor(Transformer ctx, TypeInfo typeInfo) {
		super(typeInfo.type());
		this.ctx = ctx;
		this.typeInfo = typeInfo;
	}

	@Override
	public boolean visit(AnonymousClassDeclaration node) {
		if (node.resolveBinding().isEqualTo(typeInfo.type())) {
			return true;
		}

		// Catch deeply nested closures
		// TODO avoid generating typeinfo multiple times for same class
		TypeInfo localTypeInfo = ctx.makeTypeInfo(node);

		if (localTypeInfo.closures() != null && typeInfo.closures() != null) {
			for (IVariableBinding vb : localTypeInfo.closures()) {
				if (!vb.getDeclaringMethod().getDeclaringClass()
						.isEqualTo(typeInfo.type())) {
					typeInfo.addClosure(vb);
				}
			}
		}

		return false;
	}

	@Override
	public boolean visit(Initializer node) {
		typeInfo.addInit(node);

		return super.visit(node);
	}

	@Override
	public boolean visit(SimpleName node) {
		IBinding b = node.resolveBinding();
		if (b instanceof IVariableBinding) {
			IVariableBinding vb = (IVariableBinding) b;

			if (isClosure(node, vb)) {
				typeInfo.addClosure(vb);
			}
		}

		return super.visit(node);
	}

	@Override
	public boolean visit(VariableDeclarationFragment node) {
		if (TransformUtil.initInInit(node)) {
			typeInfo.addInit(node);
		}

		return super.visit(node);
	}

	private boolean isClosure(SimpleName node, IVariableBinding vb) {
		if (vb.isField()) {
			return false;
		}

		if (vb.getDeclaringMethod() == null) {
			IJavaElement je = vb.getJavaElement().getAncestor(
					IJavaElement.INITIALIZER);

			// Could be a variable in an initializer block
			if (je == null
					|| (((IType) je.getAncestor(IJavaElement.TYPE))
							.getFullyQualifiedName().equals(((IType) type()
							.getJavaElement()).getFullyQualifiedName()))) {
				return false;
			}
		}

		if (!Modifier.isFinal(vb.getModifiers())) {
			return false;
		}
		VariableDeclarationFragment vdf = initializer(node);
		if (vdf == null && vb.getDeclaringMethod() != null) {
			IMethodBinding pmb = parentMethod(node);
			if (pmb.isEqualTo(vb.getDeclaringMethod())) {
				// Final local variable
				return false;
			}
		}
		return true;
	}

	private static IMethodBinding parentMethod(ASTNode node) {
		for (ASTNode n = node; n != null; n = n.getParent()) {
			if (n instanceof MethodDeclaration) {
				return ((MethodDeclaration) n).resolveBinding();
			}
		}

		return null;
	}

	private VariableDeclarationFragment initializer(ASTNode node) {
		for (ASTNode n = node; n != null; n = n.getParent()) {
			if (n.getParent() instanceof VariableDeclarationFragment) {
				VariableDeclarationFragment vdf = (VariableDeclarationFragment) n
						.getParent();
				if (type().isEqualTo(vdf.resolveBinding().getDeclaringClass())
						&& vdf.getInitializer() == n) {
					return vdf;
				}
			}
		}

		return null;
	}

	private ITypeBinding type() {
		return typeInfo.type();
	}
}
