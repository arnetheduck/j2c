package se.arnetheduck.j2c.transform;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.AssertStatement;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BlockComment;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EmptyStatement;
import org.eclipse.jdt.core.dom.ExpressionStatement;
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
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WildcardType;

public abstract class TransformWriter extends ASTVisitor {
	protected final ITypeBinding type;
	protected final Transformer ctx;

	protected int indent;

	protected PrintWriter out;

	protected Set<ITypeBinding> hardDeps = new TreeSet<ITypeBinding>(
			new Transformer.TypeBindingComparator());

	protected TransformWriter(Transformer ctx, final ITypeBinding type) {
		this.type = type;
		this.ctx = ctx;
	}

	public Set<ITypeBinding> getHardDeps() {
		return hardDeps;
	}

	protected void hardDep(ITypeBinding dep) {
		TransformUtil.addDep(dep, hardDeps);
		ctx.hardDep(dep);
	}

	protected void hardDep(Type type, Collection<ITypeBinding> deps) {
		hardDep(type.resolveBinding());
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

	protected void print(Object... objects) {
		TransformUtil.print(out, objects);
	}

	protected void println(Object... objects) {
		TransformUtil.println(out, objects);
	}

	protected void printi(Object... objects) {
		TransformUtil.printi(out, indent, objects);
	}

	protected void printlni(Object... objects) {
		TransformUtil.printlni(out, indent, objects);
	}

	protected String printNestedParams(Collection<IVariableBinding> closures) {
		String sep = "";
		if (TransformUtil.isInner(type)) {
			if (!TransformUtil.outerStatic(type)) {
				print(TransformUtil.outerThis(type));
				sep = ", ";
			}
		}

		if (closures != null) {
			for (IVariableBinding closure : closures) {
				print(sep, TransformUtil.relativeCName(closure.getType(), type,
						true), " ", TransformUtil.ref(closure.getType()),
						closure.getName(), "_");
				sep = ", ";
			}
		}

		return sep;
	}

	@Override
	public boolean visit(ArrayType node) {
		if (node.getComponentType().isArrayType()
				|| node.getComponentType() instanceof QualifiedType) {
			node.getComponentType().accept(this);
		} else {
			print(TransformUtil.relativeCName(node.getComponentType()
					.resolveBinding(), type, true));
		}
		ctx.softDep(node.resolveBinding());
		print("Array");

		return false;
	}

	@Override
	public boolean visit(AssertStatement node) {
		printi("/* assert");
		node.getExpression().accept(this);
		if (node.getMessage() != null) {
			print(" : ");
			node.getMessage().accept(this);
		}
		println("; */");

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
		print(node.getEscapedValue());
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
		throw new UnsupportedOperationException();
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
			print(TransformUtil.relativeCName((ITypeBinding) x, type, true));
		} else {
			Name qualifier = node.getQualifier();
			IBinding b = qualifier.resolveBinding();
			if (b instanceof ITypeBinding) {

				hardDep((ITypeBinding) b);
				print(TransformUtil.relativeCName((ITypeBinding) b, type, true),
						"::");
			} else {
				if (qualifier instanceof SimpleName
						&& b instanceof IVariableBinding) {
					IVariableBinding vb = (IVariableBinding) b;
					if (TransformUtil.isStatic(vb)
							&& !type.isSubTypeCompatible(vb.getDeclaringClass())) {
						print(TransformUtil.relativeCName(
								vb.getDeclaringClass(), type, true), "::");
					}
				}
				qualifier.accept(this);

				if (b instanceof IPackageBinding) {
					print("::");
				} else if (b instanceof IVariableBinding) {
					hardDep(((IVariableBinding) b).getType());
					print("->");
				} else {
					throw new Error("Unknown binding " + b.getClass());
				}
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
			if (vb.isField() && TransformUtil.isStatic(vb)
					&& needsQualification(node, vb.getDeclaringClass())) {
				print(TransformUtil.relativeCName(vb.getDeclaringClass(), type,
						true), "::");
				hardDep(vb.getDeclaringClass());
			}

			ctx.softDep(vb.getType());

			print(node.getIdentifier());
			print("_");
		} else if (b instanceof ITypeBinding) {
			print(TransformUtil.name((ITypeBinding) b));
			ctx.softDep((ITypeBinding) b);
		} else if (b instanceof IMethodBinding) {
			IMethodBinding mb = (IMethodBinding) b;
			if (TransformUtil.isStatic(mb)
					&& needsQualification(node, mb.getDeclaringClass())) {
				print(TransformUtil.relativeCName(mb.getDeclaringClass(), type,
						true), "::");
				hardDep(mb.getDeclaringClass());
			}

			print(TransformUtil.name(mb));
		} else {
			print(node.getIdentifier());
		}

		return false;
	}

	private boolean needsQualification(SimpleName node, ITypeBinding tb) {
		ASTNode parent = node.getParent();
		return !(parent instanceof QualifiedName)
				&& !(parent instanceof VariableDeclarationFragment)
				&& !(parent instanceof MethodDeclaration)
				&& !(parent instanceof MethodInvocation
						&& ((MethodInvocation) parent).getExpression() != null && ((MethodInvocation) parent)
						.getName() == node) && !type.isSubTypeCompatible(tb);
	}

	@Override
	public boolean visit(SimpleType node) {
		ITypeBinding tb = node.resolveBinding();
		if (tb.isNested()) {
			hardDep(tb);
		} else {
			ctx.softDep(tb);
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
		print("lit(L", node.getEscapedValue(), ")");

		hardDep(node.getAST().resolveWellKnownType("java.lang.String"));

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
		for (Iterator it = node.fragments().iterator(); it.hasNext();) {
			ASTNode e = (ASTNode) it.next();
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
		print(TransformUtil.variableModifiers(node.getModifiers()),
				TransformUtil.relativeCName(tb, type, true), " ");

		print(" ");

		visitAllCSV(node.fragments(), false);

		return false;
	}

	@Override
	public boolean visit(VariableDeclarationStatement node) {
		List<VariableDeclarationFragment> fragments = node.fragments();
		boolean hasDims = false;
		for (VariableDeclarationFragment fragment : fragments) {
			hasDims |= fragment.getExtraDimensions() > 0;
		}

		ITypeBinding tb = node.getType().resolveBinding();

		if (hasDims) {
			for (VariableDeclarationFragment fragment : fragments) {
				printi(TransformUtil.variableModifiers(node.getModifiers()));
				ITypeBinding fb = tb.createArrayType(fragment
						.getExtraDimensions());
				hardDep(fb);
				print(TransformUtil.relativeCName(fb, type, true), " ");
				fragment.accept(this);
				println(";");
			}
		} else {
			printi(TransformUtil.variableModifiers(node.getModifiers()),
					TransformUtil.relativeCName(tb, type, true), " ");

			visitAllCSV(node.fragments(), false);

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
