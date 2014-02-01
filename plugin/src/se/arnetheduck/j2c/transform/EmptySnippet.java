package se.arnetheduck.j2c.transform;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

public class EmptySnippet implements Snippet {
	@Override
	public boolean node(Transformer ctx, HeaderWriter w, ASTNode node) {
		return true;
	}

	@Override
	public boolean node(Transformer ctx, ImplWriter w, ASTNode node) {
		return true;
	}

	@Override
	public boolean type(Transformer ctx, TypeBindingHeaderWriter w,
			ITypeBinding tb) {
		return true;
	}

	@Override
	public boolean field(Transformer ctx, TypeBindingHeaderWriter w,
			IVariableBinding vb) {
		return true;
	}

	@Override
	public boolean method(Transformer ctx, TypeBindingHeaderWriter w,
			IMethodBinding mb) {
		return true;
	}

	@Override
	public boolean prefix(Transformer ctx, StubWriter w, boolean natives) {
		return true;
	}

	@Override
	public boolean suffix(Transformer ctx, StubWriter w, boolean natives) {
		return true;
	}

	@Override
	public boolean field(Transformer ctx, StubWriter w, IVariableBinding vb) {
		return true;
	}

	@Override
	public boolean method(Transformer ctx, StubWriter w, IMethodBinding mb) {
		return true;
	}

	@Override
	public boolean body(Transformer ctx, StubWriter w, IMethodBinding mb) {
		return true;
	}
}
