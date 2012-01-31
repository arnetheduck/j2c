package se.arnetheduck.j2c.transform;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Assignment.Operator;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.WhileStatement;

public class ImplWriter extends TransformWriter {
	private final IPath root;

	private StringWriter initializer;

	private ITypeBinding type;

	public ImplWriter(IPath root) {
		this.root = root;
	}

	public String getImports() {
		PrintWriter old = getOut();
		StringWriter sw = new StringWriter();
		setOut(new PrintWriter(sw));

		for (ImportDeclaration node : imports) {
			print("using ");
			if (node.isOnDemand()) {
				print("namespace ");
			}

			node.getName().accept(this);
			// TODO static imports
			println(";");
		}

		getOut().close();

		setOut(old);
		return sw.toString();
	}

	@Override
	public boolean visit(AnonymousClassDeclaration node) {
		HeaderWriter hw = new HeaderWriter(root);

		StringWriter old = initializer;
		ITypeBinding oldType = type;
		int oldIndent = indent;
		indent = 0;

		ITypeBinding tb = node.resolveBinding();

		initializer = null;
		type = tb;

		addType(tb);

		try {
			writeType(getBody(node.bodyDeclarations()), tb);
		} catch (IOException e) {
			throw new Error(e);
		}

		hw.writeAnonymousHeader(node.getAST(), tb, imports,
				node.bodyDeclarations(), closures);

		hardDeps.addAll(hw.getHardDeps());
		softDeps.addAll(hw.getSoftDeps());

		initializer = old;
		type = oldType;
		indent = oldIndent;
		return false;
	}

	@Override
	public boolean visit(ArrayAccess node) {
		addDep(node.getArray().resolveTypeBinding(), hardDeps);

		node.getArray().accept(this);
		print("->operator[](");
		node.getIndex().accept(this);
		print(")");

		return false;
	}

	@Override
	public boolean visit(ArrayCreation node) {
		print("(new ");
		ArrayType at = node.getType();
		addDep(at.resolveBinding(), hardDeps);

		at.accept(this);

		for (Iterator it = node.dimensions().iterator(); it.hasNext();) {
			print("(");
			Expression e = (Expression) it.next();
			e.accept(this);
			print(")");
			break;
		}

		if (node.getInitializer() != null) {
			node.getInitializer().accept(this);
		}
		print(")");
		return false;
	}

	@Override
	public boolean visit(ArrayInitializer node) {
		if (!(node.getParent() instanceof ArrayCreation)) {
			print("(new ");
			ITypeBinding at = node.resolveTypeBinding();
			print(TransformUtil.qualifiedCName(at));
			addDep(at, hardDeps);
		}

		print("(");
		print(node.expressions().size());

		if (!node.expressions().isEmpty()) {
			print(", ");
			visitAllCSV(node.expressions(), false);
		}

		print(")");

		if (!(node.getParent() instanceof ArrayCreation)) {
			print(")");
		}

		return false;
	}

	@Override
	public boolean visit(Assignment node) {
		ITypeBinding tb = node.getLeftHandSide().resolveTypeBinding();
		if (tb.getQualifiedName().equals("java.lang.String")
				&& node.getOperator() == Operator.PLUS_ASSIGN) {
			node.getLeftHandSide().accept(this);
			print(" = ::join(");
			node.getLeftHandSide().accept(this);
			print(", ");
			node.getRightHandSide().accept(this);
			print(")");

			return false;
		}

		node.getLeftHandSide().accept(this);

		print(" ", node.getOperator(), " ");

		node.getRightHandSide().accept(this);

		return false;
	}

	private List<Class> handledBlocks = new ArrayList<Class>(Arrays.asList(
			CatchClause.class, DoStatement.class, EnhancedForStatement.class,
			ForStatement.class, IfStatement.class, Initializer.class,
			MethodDeclaration.class, SynchronizedStatement.class,
			SwitchStatement.class, TryStatement.class, WhileStatement.class));

	@Override
	public boolean visit(Block node) {
		if (handledBlocks.contains(node.getParent().getClass())) {
			println("{");

			indent++;

			visitAll(node.statements());

			indent--;
			printlni("}");
			println();
		} else {
			System.out.println("Skipped " + node.getParent().getClass()
					+ " block");
		}

		return false;
	}

	@Override
	public boolean visit(BreakStatement node) {
		printi("break");
		if (node.getLabel() != null) {
			print(" ");
			node.getLabel().accept(this);
		}
		println(";");
		return false;
	}

	@Override
	public boolean visit(CastExpression node) {
		print(node.getType().isPrimitiveType() ? "static_cast<"
				: "dynamic_cast<");

		node.getType().accept(this);
		print(TransformUtil.ref(node.getType()));

		print(">(");

		node.getExpression().accept(this);

		print(")");
		return false;
	}

	@Override
	public boolean visit(CatchClause node) {
		print("catch (");
		node.getException().accept(this);
		addDep(node.getException().getType(), hardDeps);
		print(") ");
		node.getBody().accept(this);

		return false;
	}

	@Override
	public boolean visit(ClassInstanceCreation node) {
		if (node.getExpression() != null) {
			node.getExpression().accept(this);
			print(".");
		}

		Set<IVariableBinding> oldClosures = closures;
		closures = new HashSet<IVariableBinding>();

		print("(new ");

		if (node.getAnonymousClassDeclaration() != null) {
			ITypeBinding atb = node.getAnonymousClassDeclaration()
					.resolveBinding();
			print(TransformUtil.name(atb));
			node.getAnonymousClassDeclaration().accept(this);
			addDep(atb, hardDeps);
		} else {
			print(TransformUtil.typeArguments(node.typeArguments()));

			node.getType().accept(this);

			addDep(node.getType(), hardDeps);
		}

		String sep = "";

		print("(");
		if (node.getAnonymousClassDeclaration() != null) {
			print("this");
			sep = ", ";

			for (IVariableBinding closure : closures) {
				print(sep, closure.getName(), "_");
				sep = ", ";
			}
		}

		closures = oldClosures;

		if (!node.arguments().isEmpty()) {
			print(sep);
			Iterable<Expression> arguments = node.arguments();
			visitAllCSV(arguments, false);

			for (Expression e : arguments) {
				addDep(e.resolveTypeBinding(), hardDeps);
			}
		}

		print("))");

		return false;
	}

	@Override
	public boolean visit(ConditionalExpression node) {
		node.getExpression().accept(this);
		print(" ? ");
		node.getThenExpression().accept(this);
		print(" : ");
		node.getElseExpression().accept(this);
		return false;
	}

	@Override
	public boolean visit(ConstructorInvocation node) {
		printi(TransformUtil.typeArguments(node.typeArguments()));

		print("_construct(");

		visitAllCSV(node.arguments(), false);

		println(");");
		return false;
	}

	@Override
	public boolean visit(ContinueStatement node) {
		printi("continue");

		if (node.getLabel() != null) {
			print(" ");
			node.getLabel().accept(this);
		}
		println(";");

		return false;
	}

	@Override
	public boolean visit(DoStatement node) {
		printi("do ");
		node.getBody().accept(this);
		print(" while (");
		node.getExpression().accept(this);
		println(");");

		return false;
	}

	@Override
	public boolean visit(EnhancedForStatement node) {
		printi("for (");
		node.getParameter().accept(this);
		print(" : ");
		node.getExpression().accept(this);
		print(") ");
		node.getBody().accept(this);
		return false;
	}

	@Override
	public boolean visit(FieldAccess node) {
		node.getExpression().accept(this);
		addDep(node.getExpression().resolveTypeBinding(), hardDeps);
		print("->");
		node.getName().accept(this);
		return false;
	}

	@Override
	public boolean visit(FieldDeclaration node) {
		if ((node.getModifiers() & Modifier.STATIC) == 0) {
			return false;
		}

		boolean skip = true;

		Iterable<VariableDeclarationFragment> fragments = node.fragments();
		for (VariableDeclarationFragment f : fragments) {
			if (TransformUtil.constantValue(f) == null) {
				skip = false;
			}
		}

		if (skip) {
			return false;
		}

		if (node.getJavadoc() != null) {
			node.getJavadoc().accept(this);
		}

		printi(TransformUtil.fieldModifiers(node.getModifiers(), false,
				hasInitilializer(fragments)));

		node.getType().accept(this);

		print(" ");
		for (Iterator<VariableDeclarationFragment> it = fragments.iterator(); it
				.hasNext();) {
			VariableDeclarationFragment f = it.next();
			if (TransformUtil.constantValue(f) != null) {
				continue;
			}

			print(TransformUtil.ref(node.getType()));

			print(TransformUtil.qualifiedCName(type), "::");

			f.getName().accept(this);

			if (f.getInitializer() != null) {
				print(" = ");
				f.getInitializer().accept(this);
			}

			if (it.hasNext()) {
				print(", ");
			}
		}

		println(";");
		return false;
	}

	@Override
	public boolean visit(ForStatement node) {
		printi("for (");

		visitAllCSV(node.initializers(), false);

		print("; ");

		if (node.getExpression() != null) {
			node.getExpression().accept(this);
		}

		print("; ");

		visitAllCSV(node.updaters(), false);

		print(") ");
		node.getBody().accept(this);

		return false;
	}

	@Override
	public boolean visit(IfStatement node) {
		printi("if(");
		node.getExpression().accept(this);
		print(")");

		boolean isBlock = node.getThenStatement() instanceof Block;
		if (isBlock) {
			print(" ");
		} else {
			println();
			indent++;
		}

		node.getThenStatement().accept(this);

		if (!isBlock) {
			indent--;
		}
		if (node.getElseStatement() != null) {
			print(" else ");
			node.getElseStatement().accept(this);
		}

		return false;
	}

	@Override
	public boolean visit(InfixExpression node) {
		final List<Expression> extendedOperands = node.extendedOperands();
		ITypeBinding tb = node.resolveTypeBinding();
		if (tb != null && tb.getQualifiedName().equals("java.lang.String")) {
			print("::join(");
			for (int i = 0; i < extendedOperands.size(); ++i) {
				print("::join(");
			}

			node.getLeftOperand().accept(this);
			print(", ");
			node.getRightOperand().accept(this);
			print(")");

			for (Expression e : extendedOperands) {
				print(", ");
				e.accept(this);
				print(")");
			}
			return false;
		}

		node.getLeftOperand().accept(this);
		print(' '); // for cases like x= i - -1; or x= i++ + ++i;

		print(node.getOperator().toString());

		print(' ');

		node.getRightOperand().accept(this);

		if (!extendedOperands.isEmpty()) {
			print(' ');

			for (Expression e : extendedOperands) {
				print(node.getOperator().toString(), " ");
				e.accept(this);
			}
		}

		return false;
	}

	@Override
	public boolean visit(Initializer node) {
		if (initializer != null) {
			PrintWriter oldOut = getOut();
			setOut(new PrintWriter(initializer));

			node.getBody().accept(this);

			getOut().close();
			setOut(oldOut);

			return false;
		}

		initializer = new StringWriter();
		PrintWriter oldOut = getOut();
		setOut(new PrintWriter(initializer));

		if (node.getJavadoc() != null) {
			node.getJavadoc().accept(this);
		}

		String name = TransformUtil.name(type);
		String qcname = TransformUtil.qualifiedCName(type);

		println(qcname, "::", name, "Initializer::", name, "Initializer() {");
		indent++;

		node.getBody().accept(this);

		indent--;

		getOut().close();
		setOut(oldOut);

		return false;
	}

	private boolean closeInitializer() {
		if (initializer == null) {
			return false;
		}

		PrintWriter oldOut = getOut();

		setOut(new PrintWriter(initializer));
		printlni("}");
		println();

		String name = TransformUtil.name(type);
		String cname = TransformUtil.qualifiedCName(type);
		print(cname, "::", name, "Initializer ", cname, "::staticInitializer;");

		getOut().close();
		setOut(oldOut);

		return true;
	}

	@Override
	public boolean visit(InstanceofExpression node) {
		print("dynamic_cast<");
		node.getRightOperand().accept(this);
		addDep(node.getRightOperand(), hardDeps);
		print("*>(");
		node.getLeftOperand().accept(this);
		print(")");
		return false;
	}

	@Override
	public boolean visit(Javadoc node) {
		if (true)
			return false;
		printi("/** ");
		for (Iterator it = node.tags().iterator(); it.hasNext();) {
			ASTNode e = (ASTNode) it.next();
			e.accept(this);
		}
		println("\n */");
		return false;
	}

	@Override
	public boolean visit(LabeledStatement node) {
		printi();
		node.getLabel().accept(this);
		print(": ");
		node.getBody().accept(this);

		return false;
	}

	@Override
	public boolean visit(MethodDeclaration node) {
		if (node.getBody() == null) {
			return false;
		}

		if (node.getJavadoc() != null) {
			node.getJavadoc().accept(this);
		}

		printi(TransformUtil.typeParameters(node.typeParameters()));

		if (!node.isConstructor()) {
			node.getReturnType2().accept(this);
			print(" ", TransformUtil.ref(node.getReturnType2()));
		}

		print(TransformUtil.qualifiedCName(type), "::");

		node.getName().accept(this);

		visitAllCSV(node.parameters(), true);

		print(TransformUtil.throwsDecl(node.thrownExceptions()));

		if (node.isConstructor()) {
			for (Object o : node.getBody().statements()) {
				if (o instanceof SuperConstructorInvocation) {
					print(" : ");
					((SuperConstructorInvocation) o).accept(this);
				}
			}

			println(" {");

			indent++;

			printi("_construct(");

			printNames(node.parameters());

			println(");");

			indent--;
			println("}");
			println();

			printi("void ", TransformUtil.qualifiedCName(type), "::_construct(");

			visitAllCSV(node.parameters(), false);

			println(") {");
			indent++;

			for (Object o : node.getBody().statements()) {
				if (o instanceof SuperConstructorInvocation) {
					continue;
				}

				((Statement) o).accept(this);
			}

			indent--;
			printlni("}");
		} else {
			node.getBody().accept(this);
		}

		println();
		return false;
	}

	private void printNames(Iterable<SingleVariableDeclaration> parameters) {
		for (Iterator<SingleVariableDeclaration> it = parameters.iterator(); it
				.hasNext();) {
			SingleVariableDeclaration v = it.next();

			v.getName().accept(this);

			if (it.hasNext()) {
				print(",");
			}
		}
	}

	@Override
	public boolean visit(MethodInvocation node) {
		if (node.getExpression() != null) {
			node.getExpression().accept(this);

			IMethodBinding b = node.resolveMethodBinding();
			if ((b.getModifiers() & Modifier.STATIC) > 0) {
				print("::");
			} else {
				print("->");
			}

			addDep(node.getExpression().resolveTypeBinding(), hardDeps);
		}

		print(TransformUtil.typeArguments(node.typeArguments()));

		if (type.isNested() && node.getExpression() == null) {
			IMethodBinding mb = node.resolveMethodBinding();
			if (mb.getDeclaringClass().getKey()
					.equals(type.getDeclaringClass().getKey())) {
				TransformUtil.addDep(mb.getDeclaringClass(), hardDeps);
				print(TransformUtil.name(mb.getDeclaringClass()));
				print("_this->");
			}
		}

		node.getName().accept(this);

		Iterable<Expression> arguments = node.arguments();
		visitAllCSV(arguments, true);

		for (Expression e : arguments) {
			addDep(e.resolveTypeBinding(), hardDeps);
		}

		return false;
	}

	@Override
	public boolean visit(PackageDeclaration node) {
		pkg = node;
		return false;
	}

	@Override
	public boolean visit(PostfixExpression node) {
		node.getOperand().accept(this);
		print(node.getOperator().toString());
		return false;
	}

	@Override
	public boolean visit(PrefixExpression node) {
		print(node.getOperator().toString());
		node.getOperand().accept(this);
		return false;
	}

	@Override
	public boolean visit(ReturnStatement node) {
		printi("return");
		if (node.getExpression() != null) {
			print(" ");
			node.getExpression().accept(this);
		}

		println(";");
		return false;
	}

	@Override
	public boolean visit(SingleVariableDeclaration node) {
		ITypeBinding tb = node.getType().resolveBinding();
		if (node.getExtraDimensions() > 0) {
			tb = tb.createArrayType(node.getExtraDimensions());
			print(TransformUtil.name(tb));
		} else {
			node.getType().accept(this);
		}
		if (node.isVarargs()) {
			print("/*...*/");
		}

		print(" ");

		print(TransformUtil.ref(tb));

		node.getName().accept(this);

		if (node.getInitializer() != null) {
			print(" = ");
			node.getInitializer().accept(this);
		}

		return false;
	}

	@Override
	public boolean visit(SuperConstructorInvocation node) {
		if (node.getExpression() != null) {
			node.getExpression().accept(this);
			print(".");
		}

		print(TransformUtil.typeArguments(node.typeArguments()));

		print("super");

		visitAllCSV(node.arguments(), true);

		return false;
	}

	@Override
	public boolean visit(SuperFieldAccess node) {
		if (node.getQualifier() != null) {
			node.getQualifier().accept(this);
			print(".");
		}

		print("super::");
		node.getName().accept(this);

		return false;
	}

	@Override
	public boolean visit(SuperMethodInvocation node) {
		if (node.getQualifier() != null) {
			node.getQualifier().accept(this);
			print(".");
		}

		print("super::");

		print(TransformUtil.typeArguments(node.typeArguments()));

		node.getName().accept(this);

		visitAllCSV(node.arguments(), true);

		return false;
	}

	@Override
	public boolean visit(SwitchCase node) {
		printi();

		if (node.isDefault()) {
			println("default:");
		} else {
			print("case ");
			node.getExpression().accept(this);
			println(":");
		}

		return false;
	}

	@Override
	public boolean visit(SwitchStatement node) {
		printi("switch (");
		node.getExpression().accept(this);
		println(") {");

		indent++;

		boolean indented = false;
		List<Statement> statements = node.statements();
		for (int i = 0; i < statements.size(); ++i) {
			Statement s = statements.get(i);

			if (!indented && !(s instanceof SwitchCase)) {
				boolean isBlock = s instanceof Block;
				boolean lastStatement = i + 1 == statements.size();
				boolean nextIsCase = lastStatement
						|| statements.get(i + 1) instanceof SwitchCase;
				boolean isSingleBlock = isBlock
						&& (!nextIsCase || lastStatement);
				if (!isSingleBlock) {
					print("{");
					indent++;
					indented = true;
				}
			}
			if (indented && s instanceof SwitchCase) {
				indent--;
				printlni("}");
				indented = false;
			}

			s.accept(this);
		}

		if (indented) {
			indent--;
			printlni("}");
			indented = false;
		}

		indent--;

		printlni("}");
		println();

		return false;
	}

	@Override
	public boolean visit(SynchronizedStatement node) {
		print("synchronized (");
		node.getExpression().accept(this);
		print(") ");
		node.getBody().accept(this);

		return false;
	}

	@Override
	public boolean visit(ThisExpression node) {
		if (node.getQualifier() != null) {
			node.getQualifier().accept(this);
			print("_");
		}

		print("this");

		return false;
	}

	@Override
	public boolean visit(ThrowStatement node) {
		printi("throw ");
		node.getExpression().accept(this);
		println(";");

		return false;
	}

	@Override
	public boolean visit(TryStatement node) {
		printi("try ");
		List resources = node.resources();
		if (!node.resources().isEmpty()) {
			print('(');
			for (Iterator it = resources.iterator(); it.hasNext();) {
				VariableDeclarationExpression variable = (VariableDeclarationExpression) it
						.next();
				variable.accept(this);
				if (it.hasNext()) {
					print(';');
				}
			}
			print(')');
		}

		node.getBody().accept(this);
		print(" ");

		visitAll(node.catchClauses());

		if (node.getFinally() != null) {
			print(" finally ");
			node.getFinally().accept(this);
		}

		return false;
	}

	@Override
	public boolean visit(TypeDeclaration node) {
		StringWriter oldInitializer = initializer;
		ITypeBinding oldType = type;
		PrintWriter oldOut = getOut();
		Set<IVariableBinding> oldClosures = closures;

		final ITypeBinding tb = node.resolveBinding();

		type = tb;
		addType(tb);

		if (type.isNested()) {
			closures = new HashSet<IVariableBinding>();
		}

		try {
			StringWriter body = getBody(node.bodyDeclarations());

			writeType(body, tb);
		} catch (IOException e) {
			throw new Error(e);
		}

		initializer = oldInitializer;
		type = oldType;
		setOut(oldOut);
		closures = oldClosures;

		return false;
	}

	private StringWriter getBody(Iterable<BodyDeclaration> declarations) {
		PrintWriter old = getOut();
		StringWriter body = new StringWriter();
		setOut(new PrintWriter(body));

		visitAll(declarations);

		getOut().close();
		setOut(old);
		return body;
	}

	private void writeType(StringWriter body, final ITypeBinding tb)
			throws FileNotFoundException {
		PrintWriter old = getOut();
		try {
			setOut(new PrintWriter(new FileOutputStream(root
					+ TransformUtil.implName(tb))));

			println(TransformUtil.include(tb));

			for (ITypeBinding dep : getHardDeps()) {
				println(TransformUtil.include(dep));
			}

			println(TransformUtil.include("java.lang.h"));

			if (pkg.getJavadoc() != null) {
				pkg.getJavadoc().accept(this);
			}

			println(TransformUtil.annotations(pkg.annotations()));

			print("using namespace ");
			pkg.getName().accept(this);
			println(";");

			println(getImports());

			print(body.toString());

			if (tb.isLocal()) {
				// For local classes, synthesize base class constructors
				String qname = TransformUtil.qualifiedCName(tb);
				String name = TransformUtil.name(tb);
				for (IMethodBinding mb : tb.getSuperclass()
						.getDeclaredMethods()) {
					if (!mb.isConstructor()) {
						continue;
					}

					printi(qname, "::", name, "(");

					String sep = "";
					if (!Modifier.isStatic(tb.getModifiers())) {
						print(TransformUtil.relativeCName(
								tb.getDeclaringClass(), tb));
						print(" *" + TransformUtil.name(tb.getDeclaringClass())
								+ "_this");
						sep = ", ";
					}

					for (IVariableBinding closure : closures) {
						print(sep, TransformUtil.relativeCName(
								closure.getType(), tb), " ",
								TransformUtil.ref(closure.getType()),
								closure.getName(), "_");
						sep = ", ";
					}

					for (int i = 0; i < mb.getParameterTypes().length; ++i) {
						ITypeBinding pb = mb.getParameterTypes()[i];
						TransformUtil.addDep(pb, softDeps);

						print(sep, TransformUtil.relativeCName(pb, tb), " ",
								TransformUtil.ref(pb), "a" + i);
						sep = ", ";
					}

					println(") : ");
					indent++;
					printi(TransformUtil.relativeCName(tb.getSuperclass(), tb),
							"(");

					sep = "";
					for (int i = 0; i < mb.getParameterTypes().length; ++i) {
						print(sep, "a" + i);
						sep = ", ";
					}

					print(")");

					sep = ", ";
					if (!Modifier.isStatic(tb.getModifiers())) {
						println(", ");
						printi();
						printInit(TransformUtil.name(tb.getDeclaringClass())
								+ "_this");
					}

					for (IVariableBinding closure : closures) {
						println(", ");
						printi();
						printInit(closure.getName() + "_");
					}

					indent--;
					println(" { }");

				}
			}

			if (closeInitializer()) {
				print(initializer.toString());
			}

			getOut().close();
		} finally {
			setOut(old);
		}
	}

	private void printInit(String n) {
		print(n, "(", n, ")");
	}

	@Override
	public boolean visit(TypeLiteral node) {
		addDep(node.getType(), hardDeps);
		node.getType().accept(this);
		print("::class_");
		return false;
	}

	@Override
	public boolean visit(TypeParameter node) {
		node.getName().accept(this);
		if (!node.typeBounds().isEmpty()) {
			print(" extends ");
			for (Iterator it = node.typeBounds().iterator(); it.hasNext();) {
				Type t = (Type) it.next();
				t.accept(this);
				if (it.hasNext()) {
					print(" & ");
				}
			}
		}
		return false;
	}

	@Override
	public boolean visit(UnionType node) {
		for (Iterator it = node.types().iterator(); it.hasNext();) {
			Type t = (Type) it.next();
			t.accept(this);
			if (it.hasNext()) {
				print('|');
			}
		}
		return false;
	}

	@Override
	public boolean visit(VariableDeclarationFragment node) {
		print(TransformUtil.ref(node.resolveBinding().getType()));

		node.getName().accept(this);

		if (node.getInitializer() != null) {
			print(" = ");
			node.getInitializer().accept(this);
		}

		return false;
	}

	@Override
	public boolean visit(WhileStatement node) {
		printi("while (");
		node.getExpression().accept(this);
		print(") ");
		node.getBody().accept(this);

		return false;
	}
}
