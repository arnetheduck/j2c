package se.arnetheduck.j2c.transform;

import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.AssertStatement;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BlockComment;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EmptyStatement;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.LineComment;
import org.eclipse.jdt.core.dom.MemberRef;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodRef;
import org.eclipse.jdt.core.dom.MethodRefParameter;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WildcardType;

public abstract class TransformWriter extends ASTVisitor {
	protected final Transformer ctx;
	public final ITypeBinding type;
	protected final UnitInfo unitInfo;
	protected final DepInfo deps;

	protected int indent;

	protected PrintWriter out;

	protected TransformWriter(Transformer ctx, final ITypeBinding type,
			final UnitInfo unitInfo) {
		this.type = type;
		this.ctx = ctx;
		this.unitInfo = unitInfo;

		deps = new DepInfo(ctx);

		softDep(type);
	}

	public void softDep(ITypeBinding dep) {
		deps.soft(dep);
	}

	public void hardDep(ITypeBinding dep) {
		deps.hard(dep);
	}

	/** Some expressions are never null */
	protected boolean needsNpc(ASTNode expr) {
		if (expr instanceof ThisExpression) {
			return false;
		}

		if (expr instanceof ClassInstanceCreation) {
			return false;
		}

		if (expr instanceof ParenthesizedExpression) {
			return needsNpc(((ParenthesizedExpression) expr).getExpression());
		}

		return true;
	}

	/** Begin a null pointer check call (needs to be closed) */
	protected void npc() {
		deps.setNpc();
		print(CName.NPC + "(");
	}

	protected void staticCast(ITypeBinding tb) {
		print("static_cast< " + CName.relative(tb, type, true)
				+ TransformUtil.ref(tb) + " >(");
	}

	protected void npcAccept(Expression expr) {
		boolean needsNpc = needsNpc(expr);
		if (needsNpc) {
			npc();
		}

		expr.accept(this);

		if (needsNpc) {
			print(")");
		}
	}

	protected void visitAll(List<? extends ASTNode> nodes) {
		for (int i = 0; i < nodes.size(); ++i) {
			ASTNode node = nodes.get(i);
			node.accept(this);
			if (node instanceof Block) {
				if (i + 1 < nodes.size()) {
					ASTNode next = nodes.get(i + 1);
					if (!(next instanceof CatchClause)) {
						println();
						println();
					}
				} else {
					println();
				}
			}
		}
	}

	protected void visitAllCSV(Iterable<? extends ASTNode> nodes, boolean parens) {
		if (parens) {
			print("(");
		}

		String s = "";
		for (ASTNode node : nodes) {
			print(s);
			s = ", ";
			node.accept(this);
		}

		if (parens) {
			print(")");
		}
	}

	public boolean hasInitilializer(
			Iterable<VariableDeclarationFragment> fragments) {
		for (VariableDeclarationFragment f : fragments) {
			if (TransformUtil.constantValue(f) != null) {
				return true;
			}
		}

		return false;
	}

	public void print(String string) {
		out.print(string);
	}

	public void println(String string) {
		out.println(string);
	}

	public void println() {
		out.println();
	}

	public void printi() {
		TransformUtil.printi(out, indent, "");
	}

	public void printi(String string) {
		TransformUtil.printi(out, indent, string);
	}

	public void printlni(String string) {
		TransformUtil.printlni(out, indent, string);
	}

	@Override
	public boolean visit(ArrayType node) {
		if (node.getComponentType().isArrayType()
				|| node.getComponentType() instanceof QualifiedType) {
			node.getComponentType().accept(this);
		} else {
			print(CName.relative(node.getComponentType().resolveBinding(),
					type, true));
		}

		softDep(node.resolveBinding());
		print("Array");

		return false;
	}

	@Override
	public boolean visit(AssertStatement node) {
		printi("/* assert(");
		node.getExpression().accept(this);
		if (node.getMessage() != null) {
			print(" : ");
			node.getMessage().accept(this);
		}
		println(") */ ;");

		return false;
	}

	@Override
	public boolean visit(BlockComment node) {
		printi("/* */");
		return false;
	}

	@Override
	public boolean visit(BooleanLiteral node) {
		print(node.booleanValue() ? "true" : "false");
		return false;
	}

	@Override
	public boolean visit(CharacterLiteral node) {
		print(TransformUtil.checkConstant(node.charValue()));
		return false;
	}

	@Override
	public boolean visit(CompilationUnit node) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean visit(EmptyStatement node) {
		printlni(";");
		return false;
	}

	@Override
	public boolean visit(ExpressionStatement node) {
		printi();
		node.getExpression().accept(this);
		println(";");
		return false;
	}

	@Override
	public boolean visit(ImportDeclaration node) {
		return false;
	}

	@Override
	public boolean visit(LineComment node) {
		printlni("//");
		return false;
	}

	@Override
	public boolean visit(MemberRef node) {
		if (node.getQualifier() != null) {
			node.getQualifier().accept(this);
		}
		print("#");
		node.getName().accept(this);
		return false;
	}

	@Override
	public boolean visit(MethodRef node) {
		if (node.getQualifier() != null) {
			node.getQualifier().accept(this);
		}

		print("#");
		node.getName().accept(this);
		visitAllCSV(node.parameters(), true);

		return false;
	}

	@Override
	public boolean visit(MethodRefParameter node) {
		node.getType().accept(this);
		if (node.isVarargs()) {
			print("...");
		}
		if (node.getName() != null) {
			print(" ");
			node.getName().accept(this);
		}
		return false;
	}

	@Override
	public boolean visit(NullLiteral node) {
		print("nullptr");
		return false;
	}

	@Override
	public boolean visit(NumberLiteral node) {
		print(TransformUtil
				.checkConstant(node.resolveConstantExpressionValue()));
		return false;
	}

	@Override
	public boolean visit(PackageDeclaration node) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean visit(ParameterizedType node) {
		node.getType().accept(this);

		print(TransformUtil.typeArguments(node.typeArguments()));

		return false;
	}

	@Override
	public boolean visit(ParenthesizedExpression node) {
		print("(");
		node.getExpression().accept(this);
		print(")");
		return false;
	}

	@Override
	public boolean visit(PrimitiveType node) {
		print(TransformUtil.primitive(node.getPrimitiveTypeCode()));
		return false;
	}

	@Override
	public boolean visit(QualifiedName node) {
		IBinding x = node.resolveBinding();
		if (x instanceof ITypeBinding) {
			hardDep((ITypeBinding) x);
			print(CName.relative((ITypeBinding) x, type, true));
		} else {
			Name qualifier = node.getQualifier();
			IBinding b = qualifier.resolveBinding();
			if (b instanceof ITypeBinding) {
				hardDep((ITypeBinding) b);
				print(CName.relative((ITypeBinding) b, type, true) + "::");
			} else if (b instanceof IVariableBinding) {
				IVariableBinding vb = (IVariableBinding) b;
				hardDep(vb.getType());

				boolean hidden = false;
				if (x instanceof IVariableBinding) {
					hidden = hidden(vb.getType(), (IVariableBinding) x);
				}

				if (hidden) {
					staticCast(((IVariableBinding) x).getDeclaringClass());
				}

				npc();
				qualifier.accept(this);

				if (hidden) {
					print(")");
				}

				print(")->");

			} else if (b instanceof IPackageBinding) {
				qualifier.accept(this);
				print("::");
			} else {
				throw new Error("Unknown binding " + b.getClass());
			}

			node.getName().accept(this);
		}

		return false;
	}

	@Override
	public boolean visit(QualifiedType node) {
		node.getQualifier().accept(this);
		print("::");
		node.getName().accept(this);

		return false;
	}

	@Override
	public boolean visit(SimpleName node) {
		IBinding b = node.resolveBinding();

		if (b instanceof IVariableBinding) {
			IVariableBinding vb = (IVariableBinding) b;
			softDep(vb.getType());

			boolean qualified = false;
			if (needsQualification(node, vb.getDeclaringClass())) {
				qualify(vb.getDeclaringClass().getErasure(),
						TransformUtil.isStatic(vb));
				qualified = true;
			}

			boolean hidden = !qualified && unqualified(node)
					&& hidden(scope(node), vb);

			if (node.getParent() instanceof SuperFieldAccess) {
				print("super::");
			} else if (hidden) {
				print(CName.relative(vb.getDeclaringClass(), type, true) + "::");
			}

			print(CName.of(vb));
		} else if (b instanceof ITypeBinding) {
			print(CName.relative((ITypeBinding) b, type, true));
			softDep((ITypeBinding) b);
		} else if (b instanceof IMethodBinding) {
			IMethodBinding mb = (IMethodBinding) b;

			if (needsQualification(node, mb.getDeclaringClass())) {
				qualify(mb.getDeclaringClass().getErasure(),
						TransformUtil.isStatic(mb));
			}

			if (node.getParent() instanceof SuperMethodInvocation) {
				print("super::");
			}

			print(CName.of(mb));
		} else {
			print(node.getIdentifier());
		}

		return false;
	}

	private void qualify(ITypeBinding declaringClass, boolean isStatic) {
		if (isStatic) {
			print(CName.relative(declaringClass, type, true) + "::");
			hardDep(declaringClass);
		} else if (type.getSuperclass() != null
				&& type.getSuperclass().getErasure()
						.isEqualTo(declaringClass.getErasure())) {
			print("super::");
		} else {
			boolean found = false;
			for (ITypeBinding x = type.getSuperclass(); x != null; x = x
					.getSuperclass()) {
				if (x.isEqualTo(declaringClass)) {
					print(CName.of(x) + "::");
					found = true;
				}
			}

			if (!found) {
				// We will be this-qualifying for each type along the nesting
				// chain
				for (ITypeBinding x = type; x.getDeclaringClass() != null
						&& !x.isSubTypeCompatible(declaringClass); x = x
						.getDeclaringClass().getErasure()) {
					hardDep(x.getDeclaringClass());

					print(TransformUtil.outerThisName(x) + "->");
				}
			}
		}
	}

	protected boolean needsQualification(Name node, ITypeBinding declaringClass) {
		return node instanceof SimpleName
				&& needsQualification((SimpleName) node, declaringClass);
	}

	protected boolean needsQualification(SimpleName node,
			ITypeBinding declaringClass) {
		if (declaringClass == null) {
			return false;
		}

		declaringClass = declaringClass.getErasure();

		ASTNode parent = node.getParent();

		if (parent instanceof QualifiedName) {
			if (((QualifiedName) parent).getQualifier() != node) {
				return false;
			}
		}

		if (parent instanceof FieldAccess) {
			assert (((FieldAccess) parent).getExpression() != null);
			return false;
		}

		if (parent instanceof VariableDeclarationFragment) {
			return ((VariableDeclarationFragment) parent).getName() != node;
		}

		if (parent instanceof MethodDeclaration) {
			return false;
		}

		if (parent instanceof EnumConstantDeclaration) {
			return ((EnumConstantDeclaration) parent).getName() != node;
		}

		if (parent instanceof MethodInvocation) {
			MethodInvocation mi = (MethodInvocation) parent;
			if (type.isSubTypeCompatible(declaringClass)) {
				return false; // Method defined on a parent class
			}

			if (mi.getName() == node && mi.getExpression() != null) {
				return false;
			}
		}

		if (node.resolveBinding() instanceof IVariableBinding) {
			if (!((IVariableBinding) node.resolveBinding()).isField()) {
				return false;
			}
		}

		if (scope(node).isSubTypeCompatible(declaringClass)) {
			return false;
		}

		return true;
	}

	protected ITypeBinding scope(SimpleName node) {
		ASTNode parent = node.getParent();

		if (parent instanceof QualifiedName) {
			QualifiedName qn = (QualifiedName) parent;

			if (node == qn.getName()) {
				return qn.getQualifier().resolveTypeBinding();
			}
		} else if (parent instanceof FieldAccess) {
			FieldAccess fa = (FieldAccess) parent;
			if (node == fa.getName()) {
				return fa.getExpression().resolveTypeBinding();
			}
		} else if (parent instanceof MethodInvocation) {
			MethodInvocation mi = (MethodInvocation) parent;
			if (node == mi.getName() && mi.getExpression() != null) {
				return mi.getExpression().resolveTypeBinding();
			}
		}

		return type;
	}

	protected boolean unqualified(SimpleName node) {
		ASTNode parent = node.getParent();
		if (parent instanceof QualifiedName) {
			QualifiedName qn = (QualifiedName) parent;
			return qn.getQualifier() == node;
		}

		if (parent instanceof FieldAccess) {
			FieldAccess fa = (FieldAccess) parent;
			return fa.getExpression() == node;
		}

		if (parent instanceof MethodInvocation) {
			MethodInvocation mi = (MethodInvocation) parent;
			return mi.getExpression() == null || mi.getExpression() == node;
		}

		return true;
	}

	protected boolean hidden(ITypeBinding scope, IVariableBinding vb) {
		ITypeBinding dc = vb.getDeclaringClass();
		if (dc == null) {
			return false;
		}

		dc = dc.getErasure();
		scope = scope.getErasure();

		if (scope.isEqualTo(dc)) {
			return false;
		}

		String name = CName.of(vb);
		return hasName(scope, dc, name);
	}

	protected boolean hidden(ITypeBinding scope, IMethodBinding mb) {
		ITypeBinding dc = mb.getDeclaringClass();
		if (dc == null) {
			return false;
		}

		dc = dc.getErasure();
		scope = scope.getErasure();

		if (scope.isEqualTo(dc)) {
			return false;
		}

		String name = CName.of(mb);
		return hasName(scope, dc, name);
	}

	private boolean hasName(ITypeBinding scope, ITypeBinding dc, String name) {
		for (ITypeBinding tb = scope; tb != null
				&& !dc.isEqualTo(tb.getErasure()); tb = tb.getSuperclass()) {

			for (IMethodBinding mb : tb.getDeclaredMethods()) {
				if (name.equals(CName.of(mb))) {
					return true;
				}
			}

			for (IVariableBinding f : tb.getDeclaredFields()) {
				if (name.equals(CName.of(f))) {
					return true;
				}
			}
		}

		for (ITypeBinding tb : TypeUtil.interfaces(scope)) {
			if (tb.getErasure().isEqualTo(dc)) {
				continue;
			}

			for (IMethodBinding mb : tb.getDeclaredMethods()) {
				if (name.equals(CName.of(mb))) {
					return true;
				}
			}

			for (IVariableBinding f : tb.getDeclaredFields()) {
				if (name.equals(CName.of(f))) {
					return true;
				}
			}
		}

		return false;
	}

	@Override
	public boolean visit(SimpleType node) {
		ITypeBinding tb = node.resolveBinding();
		if (tb.isNested()) {
			hardDep(tb);
		} else {
			softDep(tb);
		}

		node.getName().accept(this);

		return false;
	}

	@Override
	public boolean visit(SingleMemberAnnotation node) {
		print("/*");
		print("@");
		node.getTypeName().accept(this);
		print("(");
		node.getValue().accept(this);
		print(")");
		print("*/");
		return false;
	}

	@Override
	public boolean visit(StringLiteral node) {
		print("u" + TransformUtil.stringLiteral(node.getEscapedValue()) + "_j");

		hardDep(node.getAST().resolveWellKnownType(String.class.getName()));

		return false;
	}

	@Override
	public boolean visit(TagElement node) {
		if (node.isNested()) {
			// nested tags are always enclosed in braces
			print("{");
		} else {
			// top-level tags always begin on a new line
			print("\n * ");
		}
		boolean previousRequiresWhiteSpace = false;
		if (node.getTagName() != null) {
			print(node.getTagName());
			previousRequiresWhiteSpace = true;
		}
		boolean previousRequiresNewLine = false;
		for (Iterator<ASTNode> it = node.fragments().iterator(); it.hasNext();) {
			ASTNode e = it.next();
			// assume text elements include necessary leading and trailing
			// whitespace
			// but Name, MemberRef, MethodRef, and nested TagElement do not
			// include white space
			boolean currentIncludesWhiteSpace = (e instanceof TextElement);
			if (previousRequiresNewLine && currentIncludesWhiteSpace) {
				print("\n * ");
			}
			previousRequiresNewLine = currentIncludesWhiteSpace;
			// add space if required to separate
			if (previousRequiresWhiteSpace && !currentIncludesWhiteSpace) {
				print(" ");
			}
			e.accept(this);
			previousRequiresWhiteSpace = !currentIncludesWhiteSpace
					&& !(e instanceof TagElement);
		}
		if (node.isNested()) {
			print("}");
		}
		return false;
	}

	@Override
	public boolean visit(TextElement node) {
		print(node.getText());
		return false;
	}

	@Override
	public boolean visit(TypeDeclarationStatement node) {
		node.getDeclaration().accept(this);
		return false;
	}

	@Override
	public boolean visit(VariableDeclarationExpression node) {
		ITypeBinding tb = node.getType().resolveBinding();
		print(TransformUtil.variableModifiers(type, node.getModifiers())
				+ CName.relative(tb, type, true) + " ");

		print(" ");

		visitAllCSV(node.fragments(), false);

		return false;
	}

	@Override
	public boolean visit(VariableDeclarationStatement node) {
		List<VariableDeclarationFragment> fragments = node.fragments();
		boolean special = false;
		for (VariableDeclarationFragment fragment : fragments) {
			special |= fragment.getExtraDimensions() > 0;
			special |= TransformUtil.isConstVar(fragment.resolveBinding());
		}

		ITypeBinding tb = node.getType().resolveBinding();

		if (special) {
			for (VariableDeclarationFragment fragment : fragments) {
				printi(TransformUtil.variableModifiers(type,
						node.getModifiers()));
				ITypeBinding fb = tb;
				if (fragment.getExtraDimensions() > 0) {
					fb = fb.createArrayType(fragment.getExtraDimensions());
				}
				softDep(fb);
				print(CName.relative(fb, type, true) + " ");
				fragment.accept(this);
				println(";");
			}
		} else {
			printi(TransformUtil.variableModifiers(type, node.getModifiers())
					+ CName.relative(tb, type, true) + " ");

			visitAllCSV(fragments, false);

			println(";");
		}

		return false;
	}

	@Override
	public boolean visit(WildcardType node) {
		print("?");
		Type bound = node.getBound();
		if (bound != null) {
			if (node.isUpperBound()) {
				print(" extends ");
			} else {
				print(" super ");
			}
			bound.accept(this);
		}
		return false;
	}
}
