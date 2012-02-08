package se.arnetheduck.j2c.transform;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
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
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.PrimitiveType.Code;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
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
	private final Set<IVariableBinding> closures;

	private StringWriter initializer;
	private List<MethodDeclaration> constructors = new ArrayList<MethodDeclaration>();
	private final List<ImportDeclaration> imports;

	private boolean needsFinally;
	private boolean needsSynchronized;

	public ImplWriter(IPath root, Transformer ctx, ITypeBinding type,
			List<ImportDeclaration> imports) {
		super(ctx, type);
		this.root = root;
		this.imports = imports;

		closures = type.isLocal() ? new HashSet<IVariableBinding>() : null;

		for (ImportDeclaration id : imports) {
			IBinding b = id.resolveBinding();
			if (b instanceof IPackageBinding) {
				ctx.packages.add((IPackageBinding) b);
			} else if (b instanceof ITypeBinding) {
				ctx.softDep((ITypeBinding) b);
			}
		}
	}

	public void write(TypeDeclaration node) throws Exception {
		StringWriter body = getBody(node.bodyDeclarations());
		writeType(body);
	}

	public void write(AnonymousClassDeclaration node) throws Exception {
		StringWriter body = getBody(node.bodyDeclarations());
		writeType(body);
	}

	private StringWriter getBody(List<BodyDeclaration> declarations) {
		StringWriter body = new StringWriter();
		out = new PrintWriter(body);

		visitAll(declarations);

		out.close();
		return body;
	}

	private void writeType(StringWriter body) throws FileNotFoundException {
		try {
			out = new PrintWriter(new FileOutputStream(root
					+ TransformUtil.implName(type)));

			println(TransformUtil.include(type));

			for (ITypeBinding dep : getHardDeps()) {
				println(TransformUtil.include(dep));
			}

			println("using namespace java::lang;");

			for (ImportDeclaration node : imports) {
				print("using ");
				if (node.isOnDemand()) {
					print("namespace ");
				}

				node.getName().accept(this);
				// TODO static imports
				println(";");
			}

			println();

			if (needsFinally) {
				makeFinally();
			}

			if (needsSynchronized) {
				makeSynchronized();
			}

			if (type.isAnonymous()) {
				makeBaseConstructors();
			} else {
				makeConstructors();
			}

			if (closeInitializer()) {
				print(initializer.toString());
			}

			print(body.toString());

			out.close();
			ctx.impls.add(type);
		} finally {
			out = null;
		}
	}

	private void makeFinally() {
		println("namespace {");
		indent++;
		printlni("template<typename F> struct finally_ {");
		indent++;
		printlni("finally_(F f) : f(f), moved(false) { }");
		printlni("finally_(finally_ &&x) : f(x.f), moved(false) { x.moved = true; }");
		printlni("~finally_() { if(!moved) f(); }");
		printlni("private: finally_(const finally_&); finally_& operator=(const finally_&); ");
		printlni("F f;");
		printlni("bool moved;");
		indent--;
		printlni("};");
		printlni("template<typename F> finally_<F> finally(F f) { return finally_<F>(f); }");
		indent--;
		printlni("}");
	}

	private void makeSynchronized() {
		println("extern void lock(java::lang::Object *);");
		println("extern void unlock(java::lang::Object *);");
		println("namespace {");
		indent++;
		printlni("struct synchronized {");
		indent++;
		printlni("synchronized(java::lang::Object *o) : o(o) { ::lock(o); }");
		printlni("~synchronized() { ::unlock(o); }");
		printlni("private: synchronized(const synchronized&); synchronized& operator=(const synchronized&); ");
		printlni("java::lang::Object *o;");
		indent--;
		printlni("};");
		indent--;
		printlni("}");
	}

	private void makeConstructors() {
		String qname = TransformUtil.qualifiedCName(type);
		String name = TransformUtil.name(type);

		if (constructors.isEmpty() && TransformUtil.isInner(type)
				|| (closures != null && !closures.isEmpty())) {
			printi(qname, "::", name, "(");
			printNestedParams(closures);
			println(") : ");
			indent++;
			String sep = "";
			if (TransformUtil.isInner(type)) {
				printi();
				printInit(TransformUtil.outerThisName(type));
				sep = ", ";
			}

			if (closures != null) {
				for (IVariableBinding closure : closures) {
					println(sep);
					printi();
					printInit(closure.getName() + "_");
					sep = ", ";
				}
			}
			println();
			println("{ }");
			indent--;
		}

		for (MethodDeclaration md : constructors) {
			printi(qname, "::", name, "(");

			String sep = printNestedParams(closures);

			if (!md.parameters().isEmpty()) {
				print(sep);

				visitAllCSV(md.parameters(), false);
			}

			println(") ", TransformUtil.throwsDecl(md.thrownExceptions()));

			if (TransformUtil.isInner(type)
					|| (closures != null && !closures.isEmpty())) {
				println(" : ");
				sep = "";
				if (TransformUtil.isInner(type)
						&& !TransformUtil.outerStatic(type)) {
					printInit(TransformUtil.outerThisName(type));
				}

				if (closures != null) {
					for (IVariableBinding closure : closures) {
						println(sep);
						printInit(closure.getName() + "_");
						sep = ", ";
					}
				}
			}
			println(" {");
			indent++;
			println("_construct(");
			printi("_construct(");

			printNames(md.parameters());

			println(");");

			indent--;
			println("}");
			println();
		}
	}

	private void makeBaseConstructors() {
		// Synthesize base class constructors
		String qname = TransformUtil.qualifiedCName(type);
		String name = TransformUtil.name(type);
		boolean fake = true;
		for (IMethodBinding mb : type.getSuperclass().getDeclaredMethods()) {
			if (!mb.isConstructor()) {
				continue;
			}

			fake = false;

			printi(qname, "::", name, "(");

			String sep = printNestedParams(closures);

			for (int i = 0; i < mb.getParameterTypes().length; ++i) {
				ITypeBinding pb = mb.getParameterTypes()[i];
				ctx.softDep(pb);

				print(sep, TransformUtil.relativeCName(pb, type), " ",
						TransformUtil.ref(pb), "a" + i);
				sep = ", ";
			}

			println(") : ");
			indent++;
			printi(TransformUtil.relativeCName(type.getSuperclass(), type), "(");

			sep = "";
			for (int i = 0; i < mb.getParameterTypes().length; ++i) {
				print(sep, "a" + i);
				sep = ", ";
			}

			print(")");

			if (TransformUtil.isInner(type) && !TransformUtil.outerStatic(type)) {
				println(", ");
				printi();
				printInit(TransformUtil.outerThisName(type));
			}

			for (IVariableBinding closure : closures) {
				println(", ");
				printi();
				printInit(closure.getName() + "_");
			}

			indent--;
			println();
			println(" { }");
			println();
		}
	}

	private void printInit(String n) {
		print(n, "(", n, ")");
	}

	@Override
	public boolean visit(AnonymousClassDeclaration node) {
		ITypeBinding tb = node.resolveBinding();
		ImplWriter iw = new ImplWriter(root, ctx, tb, imports);
		try {
			iw.write(node);
		} catch (Exception e) {
			throw new Error(e);
		}

		if (iw.closures != null && closures != null) {
			for (IVariableBinding vb : iw.closures) {
				if (!vb.getDeclaringMethod().getDeclaringClass().getKey()
						.equals(type.getKey())) {
					closures.add(vb);
				}
			}
		}

		HeaderWriter hw = new HeaderWriter(root, ctx, tb);

		hw.writeType(node.getAST(), node.bodyDeclarations(), iw.closures);

		String sep = "";

		if (!TransformUtil.outerStatic(tb)) {
			print("this");
			sep = ", ";
		}

		for (IVariableBinding closure : iw.closures) {
			print(sep, closure.getName(), "_");
			sep = ", ";
		}

		if (!((ClassInstanceCreation) node.getParent()).arguments().isEmpty()) {
			print(sep);
		}
		return false;
	}

	@Override
	public boolean visit(ArrayAccess node) {
		hardDep(node.getArray().resolveTypeBinding());

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
		hardDep(at.resolveBinding());

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
			hardDep(at);
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
			printi("}");
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
		print(" catch (");
		node.getException().accept(this);
		hardDep(node.getException().getType().resolveBinding());
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

		print("(new ");

		if (node.getAnonymousClassDeclaration() != null) {
			ITypeBinding atb = node.getAnonymousClassDeclaration()
					.resolveBinding();
			print(TransformUtil.name(atb), "(");
			node.getAnonymousClassDeclaration().accept(this);
			hardDep(atb);
		} else {
			print(TransformUtil.typeArguments(node.typeArguments()));

			node.getType().accept(this);

			hardDep(node.getType().resolveBinding());
			print("(");
		}

		String sep = "";
		if (!node.arguments().isEmpty()) {
			print(sep);
			Iterable<Expression> arguments = node.arguments();
			visitAllCSV(arguments, false);

			for (Expression e : arguments) {
				hardDep(e.resolveTypeBinding());
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

		print("_construct");

		Iterable<Expression> arguments = node.arguments();
		visitAllCSV(arguments, true);

		for (Expression e : arguments) {
			hardDep(e.resolveTypeBinding());
		}

		println(";");
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
		hardDep(node.getExpression().resolveTypeBinding());
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

	private boolean skipIndent = false;

	@Override
	public boolean visit(IfStatement node) {
		if (!skipIndent) {
			printi();
		}

		print("if(");
		skipIndent = false;

		node.getExpression().accept(this);

		print(")");

		boolean thenBlock = node.getThenStatement() instanceof Block;
		if (thenBlock) {
			print(" ");
		} else {
			println();
			indent++;
		}

		node.getThenStatement().accept(this);

		if (!thenBlock) {
			indent--;
		}

		if (node.getElseStatement() != null) {
			boolean elseif = skipIndent = node.getElseStatement() instanceof IfStatement;
			if (thenBlock) {
				print(" ");
			} else {
				printi();
			}
			print("else");
			boolean elseBlock = skipIndent
					|| node.getElseStatement() instanceof Block;
			if (elseBlock) {
				print(" ");
			} else {
				println();
				indent++;
			}

			node.getElseStatement().accept(this);

			if (!elseBlock) {
				indent--;
			}

			if (!elseif && elseBlock) {
				println();
			}
		} else {
			println();
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
		String qcname = TransformUtil.qualifiedCName(type);

		println(qcname, "::", name, "Initializer::", name, "Initializer() {");
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
		printlni("}");
		println();

		String name = TransformUtil.name(type);
		String cname = TransformUtil.qualifiedCName(type);
		print(cname, "::", name, "Initializer ", cname, "::staticInitializer;");

		out.close();
		out = oldOut;

		return true;
	}

	@Override
	public boolean visit(InstanceofExpression node) {
		print("dynamic_cast<");
		node.getRightOperand().accept(this);
		hardDep(node.getRightOperand().resolveBinding());
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
			print(TransformUtil.qualifiedCName(node.getReturnType2()
					.resolveBinding()), " ", TransformUtil.ref(node
					.getReturnType2()));
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
			print(" ");
			node.getBody().accept(this);
			println();
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

			hardDep(node.getExpression().resolveTypeBinding());
		}

		print(TransformUtil.typeArguments(node.typeArguments()));

		if (type.isNested() && node.getExpression() == null) {
			IMethodBinding mb = node.resolveMethodBinding();
			if (mb.getDeclaringClass().getKey()
					.equals(type.getDeclaringClass().getKey())) {
				TransformUtil.addDep(mb.getDeclaringClass(), hardDeps);
				if (Modifier.isStatic(mb.getModifiers())) {
					print(TransformUtil.name(mb.getDeclaringClass()), "::");
				} else {
					print(TransformUtil.thisName(mb.getDeclaringClass()), "->");
				}
			}
		}

		node.getName().accept(this);

		Iterable<Expression> arguments = node.arguments();
		visitAllCSV(arguments, true);

		for (Expression e : arguments) {
			hardDep(e.resolveTypeBinding());
		}

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
	public boolean visit(SimpleName node) {
		IBinding b = node.resolveBinding();
		if (b instanceof IVariableBinding) {
			IVariableBinding vb = (IVariableBinding) b;
			ctx.softDep(vb.getType());

			ITypeBinding ptb = parentType(node);
			if (ptb != null && ptb.isNested()
					&& !Modifier.isStatic(ptb.getModifiers())) {
				IMethodBinding pmb = parentMethod(node);

				ITypeBinding dc = vb.getDeclaringClass();
				if (vb.isField() && dc != null && ptb != null
						&& !dc.getKey().equals(ptb.getKey())) {
					boolean pq = node.getParent() instanceof QualifiedName;

					if (!pq
							|| !((QualifiedName) node.getParent()).getName()
									.equals(node)) {
						for (ITypeBinding x = ptb; x.getDeclaringClass() != null
								&& !x.getKey().equals(dc.getKey()); x = x
								.getDeclaringClass()) {
							hardDep(x.getDeclaringClass());

							if (Modifier.isStatic(vb.getModifiers())) {
								print(TransformUtil.name(x.getDeclaringClass()),
										"::");
							} else {
								print(TransformUtil.outerThisName(x), "->");
							}
						}
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
		}

		return super.visit(node);
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

		Iterable<Expression> arguments = node.arguments();
		visitAllCSV(arguments, true);

		for (Expression e : arguments) {
			hardDep(e.resolveTypeBinding());
		}

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

		Iterable<Expression> arguments = node.arguments();
		visitAllCSV(arguments, true);

		for (Expression e : arguments) {
			hardDep(e.resolveTypeBinding());
		}

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

	private int sc;

	@Override
	public boolean visit(SynchronizedStatement node) {
		printlni("{");
		indent++;
		printi("synchronized synchronized_", sc, "(");
		node.getExpression().accept(this);
		println(");");
		printi();
		node.getBody().accept(this);
		println();
		indent--;

		printlni("}");

		needsSynchronized = true;
		sc++;
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

	private int fc;

	@Override
	public boolean visit(TryStatement node) {
		if (node.getFinally() != null) {
			needsFinally = true;
			printlni("{");
			indent++;

			printi("auto finally", fc, " = finally([&] ");
			node.getFinally().accept(this);
			println(");");
			fc++;
		}

		if (!node.catchClauses().isEmpty()) {
			printi("try ");
		} else {
			printi();
		}

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

		visitAll(node.catchClauses());

		if (node.getFinally() != null) {
			indent--;
			println();
			printi("}");
			if (node.catchClauses().isEmpty()) {
				println();
			}
		}
		println();

		return false;
	}

	@Override
	public boolean visit(TypeDeclaration node) {
		ITypeBinding tb = node.resolveBinding();
		ImplWriter iw = new ImplWriter(root, ctx, tb, imports);
		try {
			iw.write(node);
		} catch (Exception e) {
			throw new Error(e);
		}

		HeaderWriter hw = new HeaderWriter(root, ctx, tb);

		hw.writeType(node.getAST(), node.bodyDeclarations(), iw.closures);

		return false;
	}

	@Override
	public boolean visit(TypeLiteral node) {
		if (node.getType().isPrimitiveType()) {
			Code code = ((PrimitiveType) node.getType()).getPrimitiveTypeCode();
			if (code.equals(PrimitiveType.BOOLEAN)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Boolean"));
				print("java::lang::Boolean::TYPE_");
			} else if (code.equals(PrimitiveType.BYTE)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Byte"));
				print("java::lang::Byte::TYPE_");
			} else if (code.equals(PrimitiveType.CHAR)) {
				hardDep(node.getAST().resolveWellKnownType(
						"java.lang.Character"));
				print("java::lang::Character::TYPE_");
			} else if (code.equals(PrimitiveType.DOUBLE)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Double"));
				print("java::lang::Double::TYPE_");
			} else if (code.equals(PrimitiveType.FLOAT)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Float"));
				print("java::lang::Float::TYPE_");
			} else if (code.equals(PrimitiveType.INT)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Integer"));
				print("java::lang::Integer::TYPE_");
			} else if (code.equals(PrimitiveType.LONG)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Long"));
				print("java::lang::Long::TYPE_");
			} else if (code.equals(PrimitiveType.SHORT)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Short"));
				print("java::lang::Short::TYPE_");
			} else if (code.equals(PrimitiveType.BOOLEAN)) {
				print("/* " + node.toString()
						+ ".class */(java::lang::Class*)0");
			}
		} else {
			hardDep(node.getType().resolveBinding());
			node.getType().accept(this);
			print("::class_");
		}
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
