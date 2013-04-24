package se.arnetheduck.j2c.transform;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

public interface Snippet {
	boolean node(Transformer ctx, HeaderWriter w, ASTNode node);

	boolean node(Transformer ctx, ImplWriter w, ASTNode node);

	boolean type(Transformer ctx, TypeBindingHeaderWriter w, ITypeBinding tb);

	boolean field(Transformer ctx, TypeBindingHeaderWriter w,
			IVariableBinding vb);

	boolean method(Transformer ctx, TypeBindingHeaderWriter w, IMethodBinding mb);

	boolean extras(Transformer ctx, StubWriter w, boolean natives);

	boolean field(Transformer ctx, StubWriter w, IVariableBinding vb);

	boolean method(Transformer ctx, StubWriter w, IMethodBinding mb);

	boolean body(Transformer ctx, StubWriter w, IMethodBinding mb);
}
