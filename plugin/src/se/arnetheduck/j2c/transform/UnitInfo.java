package se.arnetheduck.j2c.transform;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/** Contextual information about a CompilationUnit */
public class UnitInfo extends ASTVisitor {
	public final Collection<ITypeBinding> types = new ArrayList<ITypeBinding>();
	public final Collection<ImportDeclaration> imports = new ArrayList<ImportDeclaration>();

	@Override
	public boolean visit(AnonymousClassDeclaration node) {
		types.add(node.resolveBinding());
		return super.visit(node);
	}

	@Override
	public boolean visit(EnumDeclaration node) {
		types.add(node.resolveBinding());
		return super.visit(node);
	}

	@Override
	public boolean visit(ImportDeclaration node) {
		imports.add(node);
		return super.visit(node);
	}

	@Override
	public boolean visit(TypeDeclaration node) {
		types.add(node.resolveBinding());
		return super.visit(node);
	}
}
