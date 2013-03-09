package se.arnetheduck.j2c.transform;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/** init() or clinit() contents */
public class InitInfo {
	/**
	 * Strings constants that cannot be initialized directly in the header
	 */
	public List<VariableDeclarationFragment> strings = new ArrayList<VariableDeclarationFragment>();
	public List<ASTNode> nodes = new ArrayList<ASTNode>();

	public InitInfo() {
	}

	public void add(Initializer initializer) {
		nodes.add(initializer);
	}

	public void add(VariableDeclarationFragment fragment) {
		assert (TransformUtil.initInInit(fragment));
		if (TransformUtil.constantValue(fragment) instanceof String) {
			strings.add(fragment);
		} else {
			nodes.add(fragment);
		}
	}
}
