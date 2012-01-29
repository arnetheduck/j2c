package se.arnetheduck.j2c.transform;

import java.io.PrintWriter;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.AssertStatement;
import org.eclipse.jdt.core.dom.BlockComment;
import org.eclipse.jdt.core.dom.BooleanLiteral;
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
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WildcardType;

public abstract class TransformWriter extends ASTVisitor {
	protected List<ImportDeclaration> imports = new ArrayList<ImportDeclaration>();

	protected int indent;

	protected PackageDeclaration pkg;

	protected PrintWriter out;

	private Set<IPackageBinding> packages = new TreeSet<IPackageBinding>(
			new Transformer.PackageBindingComparator());
	private Set<ITypeBinding> types = new TreeSet<ITypeBinding>(
			new Transformer.TypeBindingComparator());
	protected Set<ITypeBinding> hardDeps = new TreeSet<ITypeBinding>(
			new Transformer.TypeBindingComparator());
	protected Set<ITypeBinding> softDeps = new TreeSet<ITypeBinding>(
			new Transformer.TypeBindingComparator());

	protected void printIndent(PrintWriter out) {
		for (int i = 0; i < indent; i++)
			out.print("    ");
	}

	public Set<IPackageBinding> getPackages() {
		return packages;
	}

	public Set<ITypeBinding> getTypes() {
		return types;
	}

	public Set<ITypeBinding> getHardDeps() {
		return hardDeps;
	}

	public Set<ITypeBinding> getSoftDeps() {
		return softDeps;
	}

	protected void addType(ITypeBinding tb) {
		types.add(tb);
	}

	protected void addDep(ITypeBinding dep, Collection<ITypeBinding> deps) {
		TransformUtil.addDep(dep, deps);
	}

	protected void addDep(Type type, Collection<ITypeBinding> deps) {
		addDep(type.resolveBinding(), deps);
	}

	protected void visitAll(Iterable<? extends ASTNode> nodes) {
		for (ASTNode node : nodes) {
			node.accept(this);
		}
	}

	protected void visitAllCSV(Iterable<? extends ASTNode> nodes, boolean parens) {
		if (parens) {
			out.print("(");
		}

		String s = "";
		for (ASTNode node : nodes) {
			out.print(s);
			s = ", ";
			node.accept(this);
		}

		if (parens) {
			out.print(")");
		}
	}

	protected boolean printType(PrintWriter out, Type t, boolean fqn) {
		boolean isRef = false;

		if (t instanceof ArrayType) {
			ArrayType at = (ArrayType) t;
			t = at.getElementType();
			isRef = true;
		}

		if (t instanceof PrimitiveType) {
			PrimitiveType pt = (PrimitiveType) t;
			out.print(TransformUtil.primitive(pt.getPrimitiveTypeCode()));

			return isRef;
		}

		ITypeBinding tb = t.resolveBinding();
		printType(out, tb, fqn);

		return true;
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

	protected void printType(PrintWriter out, ITypeBinding tb, boolean fqn) {
		if (fqn) {
			out.print(makeNamespace(tb));
			out.print("::");
		}

		out.print(tb.getName());
	}

	String makeNamespace(ITypeBinding type) {
		IPackageBinding p = type.getPackage();
		return p == null ? "" : p.getName().replace(".", "::");
	}

	protected boolean isRef(Type type) {
		return !(type instanceof PrimitiveType);
	}

	@Override
	public boolean visit(ArrayType node) {
		if (node.getComponentType().isArrayType()
				|| node.getComponentType() instanceof QualifiedType) {
			node.getComponentType().accept(this);
		} else {
			out.print(TransformUtil.qualifiedCName(node.getComponentType()
					.resolveBinding()));
		}
		addDep(node.resolveBinding(), softDeps);
		out.print("Array");

		return false;
	}

	@Override
	public boolean visit(AssertStatement node) {
		printIndent(out);
		out.print("/* ");
		out.print("assert ");
		node.getExpression().accept(this);
		if (node.getMessage() != null) {
			out.print(" : ");
			node.getMessage().accept(this);
		}
		out.println(";");

		out.print("*/");

		return false;
	}

	@Override
	public boolean visit(BlockComment node) {
		printIndent(out);
		out.print("/* */");
		return false;
	}

	@Override
	public boolean visit(BooleanLiteral node) {
		out.print(node.booleanValue() ? "true" : "false");
		return false;
	}

	@Override
	public boolean visit(CharacterLiteral node) {
		out.print(node.getEscapedValue());
		return false;
	}

	@Override
	public boolean visit(CompilationUnit node) {
		if (node.getPackage() != null) {
			node.getPackage().accept(this);
		}

		visitAll(node.imports());
		visitAll(node.types());

		return false;
	}

	@Override
	public boolean visit(EmptyStatement node) {
		printIndent(out);
		out.println(";");
		return false;
	}

	@Override
	public boolean visit(ExpressionStatement node) {
		printIndent(out);
		node.getExpression().accept(this);
		out.println(";");
		return false;
	}

	@Override
	public boolean visit(ImportDeclaration node) {
		imports.add(node);

		IBinding b = node.resolveBinding();
		if (b instanceof IPackageBinding) {
			packages.add((IPackageBinding) b);
		}

		return false;
	}

	@Override
	public boolean visit(LineComment node) {
		out.println("//");
		return false;
	}

	@Override
	public boolean visit(MemberRef node) {
		if (node.getQualifier() != null) {
			node.getQualifier().accept(this);
		}
		out.print("#");
		node.getName().accept(this);
		return false;
	}

	@Override
	public boolean visit(MethodRef node) {
		if (node.getQualifier() != null) {
			node.getQualifier().accept(this);
		}
		out.print("#");
		node.getName().accept(this);
		out.print("(");
		for (Iterator it = node.parameters().iterator(); it.hasNext();) {
			MethodRefParameter e = (MethodRefParameter) it.next();
			e.accept(this);
			if (it.hasNext()) {
				out.print(",");
			}
		}
		out.print(")");
		return false;
	}

	@Override
	public boolean visit(MethodRefParameter node) {
		node.getType().accept(this);
		if (node.isVarargs()) {
			out.print("...");
		}
		if (node.getName() != null) {
			out.print(" ");
			node.getName().accept(this);
		}
		return false;
	}

	@Override
	public boolean visit(NullLiteral node) {
		out.print("nullptr");
		return false;
	}

	@Override
	public boolean visit(NumberLiteral node) {
		out.print(TransformUtil.checkConstant(node
				.resolveConstantExpressionValue()));
		return false;
	}

	@Override
	public boolean visit(PackageDeclaration node) {
		pkg = node;
		return false;
	}

	@Override
	public boolean visit(ParameterizedType node) {
		node.getType().accept(this);

		out.print(TransformUtil.typeArguments(node.typeArguments()));

		return false;
	}

	@Override
	public boolean visit(ParenthesizedExpression node) {
		out.print("(");
		node.getExpression().accept(this);
		out.print(")");
		return false;
	}

	@Override
	public boolean visit(PrimitiveType node) {
		out.print(TransformUtil.primitive(node.getPrimitiveTypeCode()));
		return false;
	}

	@Override
	public boolean visit(QualifiedName node) {
		Name qualifier = node.getQualifier();

		qualifier.accept(this);

		IBinding b = qualifier.resolveBinding();
		if (b instanceof IPackageBinding) {
			out.print("::");
			packages.add((IPackageBinding) b);
		} else if (b instanceof ITypeBinding) {
			addDep((ITypeBinding) b, hardDeps);
			out.print("::");
		} else if (b instanceof IVariableBinding) {
			addDep(((IVariableBinding) b).getType(), hardDeps);
			out.print("->");
		} else {
			throw new Error("Unknown binding " + b.getClass());
		}

		node.getName().accept(this);

		return false;
	}

	@Override
	public boolean visit(QualifiedType node) {
		node.getQualifier().accept(this);
		out.print("::");
		node.getName().accept(this);

		return false;
	}

	protected Set<IVariableBinding> closures = new HashSet<IVariableBinding>();

	@Override
	public boolean visit(SimpleName node) {
		IBinding b = node.resolveBinding();

		if (b instanceof IVariableBinding) {
			IVariableBinding vb = (IVariableBinding) b;
			addDep(vb.getType(), softDeps);

			ITypeBinding ptb = parentType(node);
			if (ptb != null && ptb.isNested()
					&& !Modifier.isStatic(ptb.getModifiers())) {
				IMethodBinding pmb = parentMethod(node);

				if (vb.isField()
						&& vb.getDeclaringClass() != null
						&& ptb != null
						&& !vb.getDeclaringClass().getKey()
								.equals(ptb.getKey())) {
					if (!(node.getParent() instanceof QualifiedName)) {
						addDep(vb.getDeclaringClass(), hardDeps);
						out.print(TransformUtil.name(vb.getDeclaringClass()));
						out.print("_this->");
					}
				} else if (Modifier.isFinal(vb.getModifiers())) {
					if (pmb != null
							&& vb.getDeclaringMethod() != null
							&& !pmb.getKey().equals(
									vb.getDeclaringMethod().getKey())) {
						closures.add(vb);
					}
				}
			}

			out.print(node.getIdentifier());
			out.print("_");

		} else if (b instanceof ITypeBinding) {
			out.print(TransformUtil.name((ITypeBinding) b));
			TransformUtil.addDep((ITypeBinding) b, softDeps);
		} else if (b instanceof IMethodBinding) {
			out.print(TransformUtil.keywords(node.getIdentifier()));
		} else {
			out.print(node.getIdentifier());
		}

		return false;
	}

	private static IMethodBinding parentMethod(ASTNode node) {
		for (ASTNode n = node; n != null; n = n.getParent()) {
			if (n instanceof MethodDeclaration) {
				return ((MethodDeclaration) n).resolveBinding();
			}
		}

		return null;
	}

	private static ITypeBinding parentType(ASTNode node) {
		for (ASTNode n = node; n != null; n = n.getParent()) {
			if (n instanceof AnonymousClassDeclaration) {
				return ((AnonymousClassDeclaration) n).resolveBinding();
			}

			if (n instanceof TypeDeclaration) {
				return ((TypeDeclaration) n).resolveBinding();
			}
		}

		return null;
	}

	@Override
	public boolean visit(SimpleType node) {
		addDep(node, node.resolveBinding().isNested() ? hardDeps : softDeps);

		node.getName().accept(this);

		return false;
	}

	@Override
	public boolean visit(SingleMemberAnnotation node) {
		out.print("/*");
		out.print("@");
		node.getTypeName().accept(this);
		out.print("(");
		node.getValue().accept(this);
		out.print(")");
		out.print("*/");
		return false;
	}

	@Override
	public boolean visit(StringLiteral node) {
		out.print("lit(L");
		out.print(node.getEscapedValue());
		out.print(")");

		addDep(node.getAST().resolveWellKnownType("java.lang.String"), hardDeps);

		return false;
	}

	@Override
	public boolean visit(TagElement node) {
		if (node.isNested()) {
			// nested tags are always enclosed in braces
			out.print("{");
		} else {
			// top-level tags always begin on a new line
			out.print("\n * ");
		}
		boolean previousRequiresWhiteSpace = false;
		if (node.getTagName() != null) {
			out.print(node.getTagName());
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
				out.print("\n * ");
			}
			previousRequiresNewLine = currentIncludesWhiteSpace;
			// add space if required to separate
			if (previousRequiresWhiteSpace && !currentIncludesWhiteSpace) {
				out.print(" ");
			}
			e.accept(this);
			previousRequiresWhiteSpace = !currentIncludesWhiteSpace
					&& !(e instanceof TagElement);
		}
		if (node.isNested()) {
			out.print("}");
		}
		return false;
	}

	@Override
	public boolean visit(TextElement node) {
		out.print(node.getText());
		return false;
	}

	@Override
	public boolean visit(TypeDeclarationStatement node) {
		node.getDeclaration().accept(this);
		return false;
	}

	@Override
	public boolean visit(VariableDeclarationExpression node) {
		out.print(TransformUtil.variableModifiers(node.getModifiers()));
		node.getType().accept(this);

		out.print(" ");

		visitAllCSV(node.fragments(), false);

		return false;
	}

	@Override
	public boolean visit(VariableDeclarationStatement node) {
		printIndent(out);

		out.print(TransformUtil.variableModifiers(node.getModifiers()));

		node.getType().accept(this);
		out.print(" ");

		visitAllCSV(node.fragments(), false);

		out.println(";");
		return false;
	}

	@Override
	public boolean visit(WildcardType node) {
		out.print("?");
		Type bound = node.getBound();
		if (bound != null) {
			if (node.isUpperBound()) {
				out.print(" extends ");
			} else {
				out.print(" super ");
			}
			bound.accept(this);
		}
		return false;
	}
}
