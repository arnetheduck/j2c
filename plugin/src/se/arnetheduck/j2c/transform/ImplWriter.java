package se.arnetheduck.j2c.transform;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.AST;
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
import org.eclipse.jdt.core.dom.IBinding;
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
		PrintWriter old = out;
		StringWriter sw = new StringWriter();
		out = new PrintWriter(sw);

		for (ImportDeclaration node : imports) {
			IBinding b = node.resolveBinding();

			if (node.isOnDemand()) {
				out.print("using namespace ");
			} else {
				out.print("using ");
			}

			node.getName().accept(this);
			// TODO static imports
			out.println(";");
		}

		out.close();

		out = old;
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
		out.print("->operator[](");
		node.getIndex().accept(this);
		out.print(")");

		return false;
	}

	@Override
	public boolean visit(ArrayCreation node) {
		out.print("(new ");
		ArrayType at = node.getType();
		addDep(at.resolveBinding(), hardDeps);

		at.accept(this);

		Type elementType = at.getElementType();
		// elementType.accept(this);
		for (Iterator it = node.dimensions().iterator(); it.hasNext();) {
			out.print("(");
			Expression e = (Expression) it.next();
			e.accept(this);
			out.print(")");
			break;
		}

		if (node.getInitializer() != null) {
			node.getInitializer().accept(this);
		}
		out.print(")");
		return false;
	}

	@Override
	public boolean visit(ArrayInitializer node) {
		out.print("/*{ ");

		visitAllCSV(node.expressions(), false);

		out.print(" }*/(");
		out.print(node.expressions().size());
		out.print(")");

		return false;
	}

	@Override
	public boolean visit(Assignment node) {
		ITypeBinding tb = node.getLeftHandSide().resolveTypeBinding();
		if (tb.getQualifiedName().equals("java.lang.String")
				&& node.getOperator() == Operator.PLUS_ASSIGN) {
			node.getLeftHandSide().accept(this);
			out.print(" = join(");
			node.getLeftHandSide().accept(this);
			out.print(", ");
			node.getRightHandSide().accept(this);
			out.print(")");

			return false;
		}

		node.getLeftHandSide().accept(this);

		out.print(" ");
		out.print(node.getOperator().toString());
		out.print(" ");

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
			out.println("{");

			indent++;

			visitAll(node.statements());

			indent--;
			printIndent(out);
			out.println("}");
			out.println();
		} else {
			System.out.println("Skipped " + node.getParent().getClass()
					+ " block");
		}

		return false;
	}

	@Override
	public boolean visit(BreakStatement node) {
		printIndent(out);
		out.print("break");
		if (node.getLabel() != null) {
			out.print(" ");
			node.getLabel().accept(this);
		}
		out.println(";");
		return false;
	}

	@Override
	public boolean visit(CastExpression node) {
		out.print(node.getType().isPrimitiveType() ? "static_cast<"
				: "dynamic_cast<");

		node.getType().accept(this);
		out.print(TransformUtil.ref(node.getType()));

		out.print(">(");

		node.getExpression().accept(this);

		out.print(")");
		return false;
	}

	@Override
	public boolean visit(CatchClause node) {
		out.print("catch (");
		node.getException().accept(this);
		addDep(node.getException().getType(), hardDeps);
		out.print(") ");
		node.getBody().accept(this);

		return false;
	}

	@Override
	public boolean visit(ClassInstanceCreation node) {
		if (node.getExpression() != null) {
			node.getExpression().accept(this);
			out.print(".");
		}

		out.print("(new ");

		if (node.getAnonymousClassDeclaration() != null) {
			ITypeBinding atb = node.getAnonymousClassDeclaration()
					.resolveBinding();
			out.print(TransformUtil.name(atb));
			node.getAnonymousClassDeclaration().accept(this);
			addDep(atb, hardDeps);
		} else {
			out.print(TransformUtil.typeArguments(node.typeArguments()));

			node.getType().accept(this);

			addDep(node.getType(), hardDeps);
		}

		String sep = "";

		out.print("(");
		if (node.getAnonymousClassDeclaration() != null) {
			out.print("this");
			sep = ", ";

			for (IVariableBinding closure : closures) {
				out.print(sep);
				sep = ", ";
				out.print(closure.getName());
				out.print("_");
			}
		}

		closures.clear();

		if (!node.arguments().isEmpty()) {
			out.print(sep);
			Iterable<Expression> arguments = node.arguments();
			visitAllCSV(arguments, false);

			for (Expression e : arguments) {
				addDep(e.resolveTypeBinding(), hardDeps);
			}
		}

		out.print("))");

		return false;
	}

	@Override
	public boolean visit(ConditionalExpression node) {
		node.getExpression().accept(this);
		out.print(" ? ");
		node.getThenExpression().accept(this);
		out.print(" : ");
		node.getElseExpression().accept(this);
		return false;
	}

	@Override
	public boolean visit(ConstructorInvocation node) {
		printIndent(out);

		out.print(TransformUtil.typeArguments(node.typeArguments()));

		out.print("_construct(");

		visitAllCSV(node.arguments(), false);

		out.println(");");
		return false;
	}

	@Override
	public boolean visit(ContinueStatement node) {
		printIndent(out);
		out.print("continue");

		if (node.getLabel() != null) {
			out.print(" ");
			node.getLabel().accept(this);
		}
		out.println(";");

		return false;
	}

	@Override
	public boolean visit(DoStatement node) {
		printIndent(out);
		out.print("do ");
		node.getBody().accept(this);
		out.print(" while (");
		node.getExpression().accept(this);
		out.println(");");
		return false;
	}

	@Override
	public boolean visit(EnhancedForStatement node) {
		printIndent(out);
		out.print("for (");
		node.getParameter().accept(this);
		out.print(" : ");
		node.getExpression().accept(this);
		out.print(") ");
		node.getBody().accept(this);
		return false;
	}

	@Override
	public boolean visit(FieldAccess node) {
		node.getExpression().accept(this);
		addDep(node.getExpression().resolveTypeBinding(), hardDeps);
		out.print("->");
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
		printIndent(out);

		out.print(TransformUtil.fieldModifiers(node.getModifiers(), false,
				hasInitilializer(fragments)));

		node.getType().accept(this);

		out.print(" ");
		for (Iterator<VariableDeclarationFragment> it = fragments.iterator(); it
				.hasNext();) {
			VariableDeclarationFragment f = it.next();
			if (TransformUtil.constantValue(f) != null) {
				continue;
			}

			out.print(TransformUtil.ref(node.getType()));

			out.print(TransformUtil.qualifiedCName(type));
			out.print("::");

			f.getName().accept(this);

			if (f.getInitializer() != null) {
				out.print(" = ");
				f.getInitializer().accept(this);
			}

			if (it.hasNext()) {
				out.print(", ");
			}
		}

		out.println(";");
		return false;
	}

	@Override
	public boolean visit(ForStatement node) {
		printIndent(out);
		out.print("for (");

		visitAllCSV(node.initializers(), false);

		out.print("; ");

		if (node.getExpression() != null) {
			node.getExpression().accept(this);
		}

		out.print("; ");

		visitAllCSV(node.updaters(), false);

		out.print(") ");
		node.getBody().accept(this);

		return false;
	}

	@Override
	public boolean visit(IfStatement node) {
		printIndent(out);

		out.print("if(");
		node.getExpression().accept(this);
		out.print(")");

		boolean isBlock = node.getThenStatement() instanceof Block;
		if (isBlock) {
			out.print(" ");
		} else {
			out.println();
			indent++;
		}

		node.getThenStatement().accept(this);

		if (!isBlock) {
			indent--;
		}
		if (node.getElseStatement() != null) {
			out.print(" else ");
			node.getElseStatement().accept(this);
		}

		return false;
	}

	@Override
	public boolean visit(InfixExpression node) {
		final List<Expression> extendedOperands = node.extendedOperands();
		ITypeBinding tb = node.resolveTypeBinding();
		if (tb != null && tb.getQualifiedName().equals("java.lang.String")) {
			out.print("join(");
			for (int i = 0; i < extendedOperands.size(); ++i) {
				out.print("join(");
			}

			node.getLeftOperand().accept(this);
			out.print(", ");
			node.getRightOperand().accept(this);
			out.print(")");

			for (Expression e : extendedOperands) {
				out.print(", ");
				e.accept(this);
				out.print(")");
			}
			return false;
		}

		node.getLeftOperand().accept(this);
		out.print(' '); // for cases like x= i - -1; or x= i++ + ++i;

		out.print(node.getOperator().toString());

		out.print(' ');

		node.getRightOperand().accept(this);

		if (!extendedOperands.isEmpty()) {
			out.print(' ');

			for (Expression e : extendedOperands) {
				out.print(node.getOperator().toString());
				out.print(' ');
				e.accept(this);
			}
		}

		return false;
	}

	@Override
	public boolean visit(Initializer node) {
		if (initializer != null) {
			PrintWriter oldOut = out;
			out = new PrintWriter(initializer);

			node.getBody().accept(this);

			out.close();
			out = oldOut;

			return false;
		}

		initializer = new StringWriter();
		PrintWriter oldOut = out;
		out = new PrintWriter(initializer);

		if (node.getJavadoc() != null) {
			node.getJavadoc().accept(this);
		}

		String name = TransformUtil.name(type);

		out.print(TransformUtil.qualifiedCName(type));

		out.print("::");
		out.print(name);
		out.print("Initializer::");
		out.print(name);
		out.println("Initializer() {");
		indent++;

		node.getBody().accept(this);

		indent--;

		out.close();
		out = oldOut;

		return false;
	}

	private boolean closeInitializer() {
		if (initializer == null) {
			return false;
		}

		PrintWriter oldOut = out;

		out = new PrintWriter(initializer);
		printIndent(out);
		out.println("}");
		out.println();

		String name = TransformUtil.name(type);
		String cname = TransformUtil.qualifiedCName(type);
		out.print(cname);

		out.print("::");
		out.print(name);
		out.print("Initializer ");
		out.print(cname);
		out.println("::staticInitializer;");
		out.close();
		out = oldOut;

		return true;
	}

	@Override
	public boolean visit(InstanceofExpression node) {
		out.print("dynamic_cast<");
		node.getRightOperand().accept(this);
		addDep(node.getRightOperand(), hardDeps);
		out.print("*>(");
		node.getLeftOperand().accept(this);
		out.print(")");
		return false;
	}

	@Override
	public boolean visit(Javadoc node) {
		if (true)
			return false;
		printIndent(out);
		out.print("/** ");
		for (Iterator it = node.tags().iterator(); it.hasNext();) {
			ASTNode e = (ASTNode) it.next();
			e.accept(this);
		}
		out.println("\n */");
		return false;
	}

	@Override
	public boolean visit(LabeledStatement node) {
		printIndent(out);
		node.getLabel().accept(this);
		out.print(": ");
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

		printIndent(out);

		if (!node.typeParameters().isEmpty()) {
			out.print("/*<");
			for (Iterator it = node.typeParameters().iterator(); it.hasNext();) {
				TypeParameter t = (TypeParameter) it.next();
				t.accept(this);
				if (it.hasNext()) {
					out.print(",");
				}
			}
			out.print(">*/");
		}

		if (!node.isConstructor()) {
			node.getReturnType2().accept(this);
			out.print(" ");
			out.print(TransformUtil.ref(node.getReturnType2()));
		}

		out.print(TransformUtil.qualifiedCName(type));
		out.print("::");
		node.getName().accept(this);

		visitAllCSV(node.parameters(), true);

		out.print(TransformUtil.throwsDecl(node.thrownExceptions()));

		if (node.isConstructor()) {
			for (Object o : node.getBody().statements()) {
				if (o instanceof SuperConstructorInvocation) {
					out.print(" : ");
					((SuperConstructorInvocation) o).accept(this);
				}
			}

			out.println(" {");

			indent++;

			printIndent(out);
			out.print("_construct(");

			printNames(node.parameters());

			out.println(");");

			indent--;
			out.println("}");
			out.println();

			printIndent(out);
			out.print("void ");
			out.print(TransformUtil.qualifiedCName(type));
			out.print("::_construct(");

			visitAllCSV(node.parameters(), false);

			out.println(") {");
			indent++;

			for (Object o : node.getBody().statements()) {
				if (o instanceof SuperConstructorInvocation) {
					continue;
				}

				((Statement) o).accept(this);
			}

			indent--;
			printIndent(out);
			out.println("}");
		} else {
			node.getBody().accept(this);
		}

		out.println();
		return false;
	}

	private void printNames(Iterable<SingleVariableDeclaration> parameters) {
		for (Iterator<SingleVariableDeclaration> it = parameters.iterator(); it
				.hasNext();) {
			SingleVariableDeclaration v = it.next();

			v.getName().accept(this);

			if (it.hasNext()) {
				out.print(",");
			}
		}
	}

	@Override
	public boolean visit(MethodInvocation node) {
		if (node.getExpression() != null) {
			node.getExpression().accept(this);

			IMethodBinding b = node.resolveMethodBinding();
			if ((b.getModifiers() & Modifier.STATIC) > 0) {
				out.print("::");
			} else {
				out.print("->");
			}

			addDep(node.getExpression().resolveTypeBinding(), hardDeps);
		}

		out.print(TransformUtil.typeArguments(node.typeArguments()));

		if (type.isNested()) {
			IMethodBinding mb = node.resolveMethodBinding();
			if (mb.getDeclaringClass().getKey()
					.equals(type.getDeclaringClass().getKey())) {
				out.print(TransformUtil.name(mb.getDeclaringClass()));
				out.print("_this->");
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
		out.print(node.getOperator().toString());
		return false;
	}

	@Override
	public boolean visit(PrefixExpression node) {
		out.print(node.getOperator().toString());
		node.getOperand().accept(this);
		return false;
	}

	@Override
	public boolean visit(ReturnStatement node) {
		printIndent(out);

		out.print("return");
		if (node.getExpression() != null) {
			out.print(" ");
			node.getExpression().accept(this);
		}

		out.println(";");
		return false;
	}

	@Override
	public boolean visit(SingleVariableDeclaration node) {
		printIndent(out);

		out.print(TransformUtil.variableModifiers(node.getModifiers()));
		node.getType().accept(this);

		if (node.isVarargs()) {
			out.print("...");
		}

		out.print(" ");

		out.print(TransformUtil.ref(node.getType()));

		node.getName().accept(this);

		if (node.getInitializer() != null) {
			out.print(" = ");
			node.getInitializer().accept(this);
		}

		return false;
	}

	@Override
	public boolean visit(SuperConstructorInvocation node) {
		if (node.getExpression() != null) {
			node.getExpression().accept(this);
			out.print(".");
		}

		out.print(TransformUtil.typeArguments(node.typeArguments()));

		out.print("super");

		visitAllCSV(node.arguments(), true);

		return false;
	}

	@Override
	public boolean visit(SuperFieldAccess node) {
		if (node.getQualifier() != null) {
			node.getQualifier().accept(this);
			out.print(".");
		}

		out.print("super::");
		node.getName().accept(this);

		return false;
	}

	@Override
	public boolean visit(SuperMethodInvocation node) {
		if (node.getQualifier() != null) {
			node.getQualifier().accept(this);
			out.print(".");
		}

		out.print("super::");

		out.print(TransformUtil.typeArguments(node.typeArguments()));

		node.getName().accept(this);

		visitAllCSV(node.arguments(), true);

		return false;
	}

	@Override
	public boolean visit(SwitchCase node) {
		printIndent(out);

		if (node.isDefault()) {
			out.println("default:");
		} else {
			out.print("case ");
			node.getExpression().accept(this);
			out.println(":");
		}

		return false;
	}

	@Override
	public boolean visit(SwitchStatement node) {
		printIndent(out);

		out.print("switch (");
		node.getExpression().accept(this);
		out.print(") ");

		out.println("{");

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
					out.print("{");
					indent++;
					indented = true;
				}
			}
			if (indented && s instanceof SwitchCase) {
				indent--;
				printIndent(out);
				out.println("}");
				indented = false;
			}

			s.accept(this);
		}

		if (indented) {
			indent--;
			printIndent(out);
			out.println("}");
			indented = false;
		}

		indent--;

		printIndent(out);
		out.println("}");
		out.println();

		return false;
	}

	@Override
	public boolean visit(SynchronizedStatement node) {
		out.print("synchronized (");
		node.getExpression().accept(this);
		out.print(") ");
		node.getBody().accept(this);

		return false;
	}

	@Override
	public boolean visit(ThisExpression node) {
		if (node.getQualifier() != null) {
			node.getQualifier().accept(this);
			out.print("_");
		}

		out.print("this");

		return false;
	}

	@Override
	public boolean visit(ThrowStatement node) {
		printIndent(out);
		out.print("throw ");
		node.getExpression().accept(this);
		out.println(";");

		return false;
	}

	@Override
	public boolean visit(TryStatement node) {
		printIndent(out);
		out.print("try ");
		List resources = node.resources();
		if (node.getAST().apiLevel() >= AST.JLS4) {
			if (!node.resources().isEmpty()) {
				out.print('(');
				for (Iterator it = resources.iterator(); it.hasNext();) {
					VariableDeclarationExpression variable = (VariableDeclarationExpression) it
							.next();
					variable.accept(this);
					if (it.hasNext()) {
						out.print(';');
					}
				}
				out.print(')');
			}
		}

		node.getBody().accept(this);
		out.print(" ");

		visitAll(node.catchClauses());

		if (node.getFinally() != null) {
			out.print(" finally ");
			node.getFinally().accept(this);
		}

		return false;
	}

	@Override
	public boolean visit(TypeDeclaration node) {
		StringWriter oldInitializer = initializer;
		ITypeBinding oldType = type;
		PrintWriter oldOut = out;

		final ITypeBinding tb = node.resolveBinding();

		type = tb;
		addType(tb);

		try {
			StringWriter body = getBody(node.bodyDeclarations());

			writeType(body, tb);
		} catch (IOException e) {
			throw new Error(e);
		}

		initializer = oldInitializer;
		type = oldType;
		out = oldOut;

		return false;
	}

	private StringWriter getBody(Iterable<BodyDeclaration> declarations) {
		PrintWriter old = out;
		StringWriter body = new StringWriter();
		out = new PrintWriter(body);

		visitAll(declarations);

		out.close();
		out = old;
		return body;
	}

	private void writeType(StringWriter body, final ITypeBinding tb)
			throws FileNotFoundException {
		PrintWriter old = out;
		try {
			out = new PrintWriter(new FileOutputStream(root
					+ TransformUtil.implName(tb)));

			out.println(TransformUtil.include(tb.getPackage()));

			for (ITypeBinding dep : getHardDeps()) {
				out.println(TransformUtil.include(dep));
			}

			out.println(TransformUtil.include("java.lang.h"));

			if (pkg.getJavadoc() != null) {
				pkg.getJavadoc().accept(this);
			}

			out.println(TransformUtil.annotations(pkg.annotations()));

			printIndent(out);

			out.print("using namespace ");
			pkg.getName().accept(this);
			out.println(";");

			out.println(getImports());

			out.print(body.toString());

			if (tb.isLocal()) {
				// For local classes, synthesize base class constructors
				String qname = TransformUtil.qualifiedCName(tb);
				String name = TransformUtil.name(tb);
				for (IMethodBinding mb : tb.getSuperclass()
						.getDeclaredMethods()) {
					if (!mb.isConstructor()) {
						continue;
					}

					out.print(TransformUtil.indent(indent));
					out.print(qname);
					out.print("::");
					out.print(name);

					out.print("(");

					String sep = "";
					if (!Modifier.isStatic(tb.getModifiers())) {
						out.print(TransformUtil.relativeCName(
								tb.getDeclaringClass(), tb));
						out.print(" *"
								+ TransformUtil.name(tb.getDeclaringClass())
								+ "_this");
						sep = ", ";
					}

					for (IVariableBinding closure : closures) {
						out.print(sep);
						sep = ", ";
						out.print(TransformUtil.relativeCName(
								closure.getType(), tb));
						out.print(" ");
						out.print(closure.getName());
						out.print("_");
					}

					for (int i = 0; i < mb.getParameterTypes().length; ++i) {
						out.print(sep);
						sep = ", ";

						ITypeBinding pb = mb.getParameterTypes()[i];
						TransformUtil.addDep(pb, softDeps);

						out.print(TransformUtil.relativeCName(pb, tb));
						out.print(" ");
						out.print(TransformUtil.ref(pb));
						out.print("a" + i);
					}

					out.println(") : ");
					indent++;
					out.print(TransformUtil.indent(indent));
					out.print(TransformUtil.relativeCName(tb.getSuperclass(),
							tb));

					out.print("(");
					sep = "";
					for (int i = 0; i < mb.getParameterTypes().length; ++i) {
						out.print(sep);
						sep = ", ";
						out.print("a" + i);
					}

					out.print(")");

					sep = ", ";
					if (!Modifier.isStatic(tb.getModifiers())) {
						out.println(", ");
						out.print(TransformUtil.indent(indent));
						printInit(TransformUtil.name(tb.getDeclaringClass())
								+ "_this");
					}

					for (IVariableBinding closure : closures) {
						out.println(", ");
						out.print(TransformUtil.indent(indent));
						printInit(closure.getName() + "_");
					}

					indent--;
					out.println("{ }");

				}
			}

			if (closeInitializer()) {
				out.print(initializer.toString());
			}

			out.close();
		} finally {
			out = old;
		}
	}

	private void printInit(String n) {
		out.print(n);
		out.print("(");
		out.print(n);
		out.print(")");
	}

	@Override
	public boolean visit(TypeLiteral node) {
		node.getType().accept(this);
		out.print(".class");
		return false;
	}

	@Override
	public boolean visit(TypeParameter node) {
		node.getName().accept(this);
		if (!node.typeBounds().isEmpty()) {
			out.print(" extends ");
			for (Iterator it = node.typeBounds().iterator(); it.hasNext();) {
				Type t = (Type) it.next();
				t.accept(this);
				if (it.hasNext()) {
					out.print(" & ");
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
				out.print('|');
			}
		}
		return false;
	}

	@Override
	public boolean visit(VariableDeclarationFragment node) {
		out.print(TransformUtil.ref(node.resolveBinding().getType()));

		node.getName().accept(this);

		if (node.getInitializer() != null) {
			out.print(" = ");
			node.getInitializer().accept(this);
		}

		return false;
	}

	@Override
	public boolean visit(WhileStatement node) {
		printIndent(out);
		out.print("while (");
		node.getExpression().accept(this);
		out.print(") ");
		node.getBody().accept(this);

		return false;
	}
}
