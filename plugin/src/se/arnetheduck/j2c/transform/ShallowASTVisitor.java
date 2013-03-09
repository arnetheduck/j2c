package se.arnetheduck.j2c.transform;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/** Visit the nodes of a single type */
public class ShallowASTVisitor extends ASTVisitor {

	private ITypeBinding type;

	public ShallowASTVisitor(ITypeBinding type) {
		this.type = type;
	}

	@Override
	public boolean visit(AnnotationTypeDeclaration node) {
		return node.resolveBinding().isEqualTo(type);
	}

	@Override
	public boolean visit(AnonymousClassDeclaration node) {
		return node.resolveBinding().isEqualTo(type);
	}

	@Override
	public boolean visit(EnumDeclaration node) {
		return node.resolveBinding().isEqualTo(type);
	}

	@Override
	public boolean visit(TypeDeclaration node) {
		return node.resolveBinding().isEqualTo(type);
	}
}
