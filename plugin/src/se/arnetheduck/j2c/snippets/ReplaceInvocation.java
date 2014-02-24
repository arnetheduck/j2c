package se.arnetheduck.j2c.snippets;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeLiteral;

import se.arnetheduck.j2c.transform.CName;
import se.arnetheduck.j2c.transform.EmptySnippet;
import se.arnetheduck.j2c.transform.ImplWriter;
import se.arnetheduck.j2c.transform.Transformer;

public class ReplaceInvocation extends EmptySnippet {

	@Override
	public boolean node(Transformer ctx, ImplWriter w, ASTNode node) {
		if (node instanceof MethodInvocation) {
			return replace(w, (MethodInvocation) node);
		}

		return super.node(ctx, w, node);
	}

	private static boolean replace(ImplWriter w, MethodInvocation node) {
		if (replaceEnsureClassInitialized(w, node)) {
			return false;
		}

		return true;
	}

	private static boolean replaceEnsureClassInitialized(ImplWriter w,
			MethodInvocation node) {
		if (!node.getName().getIdentifier().equals("ensureClassInitialized")) {
			return false;
		}

		if (node.arguments().size() != 1
				|| !(node.arguments().get(0) instanceof TypeLiteral)) {
			return false;
		}

		IMethodBinding mb = node.resolveMethodBinding();
		if (!mb.getDeclaringClass().getQualifiedName()
				.equals("sun.misc.Unsafe")) {
			return false;
		}

		TypeLiteral tl = (TypeLiteral) node.arguments().get(0);
		ITypeBinding tb = tl.getType().resolveBinding();
		w.hardDep(tb);
		w.print(CName.relative(tb, w.type, true) + "::clinit()");

		return true;
	}
}
