package se.arnetheduck.j2c.transform;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

public class TypeInfoVisitor extends ASTVisitor {
	private UnitInfo unitInfo;
	private TypeInfo typeInfo;

	public TypeInfoVisitor(UnitInfo unitInfo) {
		this.unitInfo = unitInfo;
	}

	@Override
	public boolean visit(AnnotationTypeDeclaration node) {
		typeInfo = new TypeInfo(typeInfo, node.resolveBinding());
		unitInfo.types.put(typeInfo.type(), typeInfo);
		return true;
	}

	@Override
	public void endVisit(AnnotationTypeDeclaration node) {
		typeInfo = typeInfo.parent();
	}

	@Override
	public boolean visit(AnonymousClassDeclaration node) {
		typeInfo = new TypeInfo(typeInfo, node.resolveBinding());
		unitInfo.types.put(typeInfo.type(), typeInfo);
		return true;
	}

	@Override
	public void endVisit(AnonymousClassDeclaration node) {
		TypeInfo anonType = typeInfo;
		typeInfo = typeInfo.parent();

		if (typeInfo.closures() != null && anonType.closures() != null) {
			for (IVariableBinding vb : anonType.closures()) {
				if (!vb.getDeclaringMethod().getDeclaringClass()
						.isEqualTo(typeInfo.type())) {
					typeInfo.addClosure(vb);
				}
			}
		}
	}

	@Override
	public boolean visit(EnumDeclaration node) {
		typeInfo = new TypeInfo(typeInfo, node.resolveBinding());
		unitInfo.types.put(typeInfo.type(), typeInfo);
		return true;
	}

	@Override
	public void endVisit(EnumDeclaration node) {
		typeInfo = typeInfo.parent();
	}

	@Override
	public boolean visit(MethodDeclaration node) {
		if (Modifier.isNative(node.getModifiers())) {
			typeInfo.setHasNatives();
		}
		return true;
	}

	@Override
	public boolean visit(TypeDeclaration node) {
		typeInfo = new TypeInfo(typeInfo, node.resolveBinding());
		unitInfo.types.put(typeInfo.type(), typeInfo);
		return true;
	}

	@Override
	public void endVisit(TypeDeclaration node) {
		typeInfo = typeInfo.parent();
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
