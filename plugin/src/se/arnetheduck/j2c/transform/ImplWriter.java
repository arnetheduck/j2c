package se.arnetheduck.j2c.transform;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
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
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.PrimitiveType.Code;
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
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;

public class ImplWriter extends TransformWriter {
	private static final String FINALLY_HPP = "/se/arnetheduck/j2c/resources/finally.hpp";
	private static final String SYNCHRONIZED_HPP = "/se/arnetheduck/j2c/resources/synchronized.hpp";

	private static final String i1 = TransformUtil.indent(1);

	private final IPath root;
	private final Set<IVariableBinding> closures;

	/** Constant expression fields that cannot be represented as such in C++ */
	private ArrayList<VariableDeclarationFragment> consts = new ArrayList<VariableDeclarationFragment>();

	/** Static initializers */
	private StringWriter clinit;

	/** Instance initializers */
	private StringWriter init;

	private List<MethodDeclaration> constructors = new ArrayList<MethodDeclaration>();
	private List<VariableDeclarationFragment> fields = new ArrayList<VariableDeclarationFragment>();

	public final Map<ITypeBinding, ImplWriter> localTypes = new TreeMap<ITypeBinding, ImplWriter>(
			new BindingComparator());

	private boolean needsFinally;
	private boolean needsSynchronized;

	private boolean hasNatives;

	private final Impl impl;
	private final String qcname;
	private final String name;

	private final List<List<String>> locals = new ArrayList<List<String>>();

	public ImplWriter(IPath root, Transformer ctx, ITypeBinding type,
			UnitInfo unitInfo) {
		super(ctx, type, unitInfo);
		this.root = root;

		closures = type.isLocal() ? new LinkedHashSet<IVariableBinding>()
				: null;

		impl = new Impl(ctx, type, deps);
		qcname = CName.qualified(type, true);
		name = CName.of(type);
	}

	public void write(AnnotationTypeDeclaration node) throws Exception {
		String body = getBody(node.bodyDeclarations());
		writeType(body);

		HeaderWriter hw = new HeaderWriter(root, ctx, type, unitInfo, closures);
		hw.write(node);
	}

	public void write(AnonymousClassDeclaration node) throws Exception {
		String body = getBody(node.bodyDeclarations());
		writeType(body);

		HeaderWriter hw = new HeaderWriter(root, ctx, type, unitInfo, closures);
		hw.write(node);
	}

	public void write(EnumDeclaration node) throws Exception {
		String body = getBody(node.enumConstants(), node.bodyDeclarations());
		writeType(body);

		HeaderWriter hw = new HeaderWriter(root, ctx, type, unitInfo, closures);
		hw.write(node);
	}

	public void write(TypeDeclaration node) throws Exception {
		String body = getBody(node.bodyDeclarations());
		writeType(body);

		HeaderWriter hw = new HeaderWriter(root, ctx, type, unitInfo, closures);
		hw.write(node);
	}

	private String getBody(List<BodyDeclaration> declarations) {
		return getBody(new ArrayList<EnumConstantDeclaration>(), declarations);
	}

	private String getBody(List<EnumConstantDeclaration> enums,
			List<BodyDeclaration> declarations) {
		StringWriter body = new StringWriter();
		out = new PrintWriter(body);

		visitAll(enums);

		visitAll(declarations);

		out.close();
		return body.toString();
	}

	private void writeType(String body) throws Exception {
		String extras = getExtras();
		String cinit = getCinit();

		impl.write(root, extras + body, "", closures, cinit, clinit, fmod,
				false);

		ctx.addImpl(type);

		if (hasNatives) {
			StubWriter sw = new StubWriter(root, ctx, type);
			sw.write(true, true);
		}
	}

	private String getExtras() throws IOException {
		StringWriter sw = new StringWriter();
		out = new PrintWriter(sw);

		printFinally();
		printSynchronized();

		printDefaultInitCtor();
		printCtors();
		printAnonCtors();

		if (!(type.isInterface() || type.isAnnotation())) {
			printInit();
		}

		out.close();
		out = null;

		return sw.toString();
	}

	private String getCinit() {
		if (consts.isEmpty()) {
			return null;
		}

		int oi = indent;
		indent = 1;

		PrintWriter old = out;
		StringWriter sw = new StringWriter();
		out = new PrintWriter(sw);
		for (VariableDeclarationFragment vdf : consts) {
			printi();
			vdf.getName().accept(this);
			print(" = ");
			vdf.getInitializer().accept(this);
			println(";");
		}

		indent = oi;

		out.close();
		out = old;

		return sw.toString();
	}

	private void printFinally() throws IOException {
		if (!needsFinally) {
			return;
		}

		try (InputStream is = ArrayWriter.class
				.getResourceAsStream(FINALLY_HPP)) {
			print(TransformUtil.read(is));
		}
	}

	private void printSynchronized() throws IOException {
		if (!needsSynchronized) {
			return;
		}

		try (InputStream is = ArrayWriter.class
				.getResourceAsStream(SYNCHRONIZED_HPP)) {
			print(TransformUtil.read(is));
		}
	}

	private void printInit() {
		if (init == null) {
			return;
		}

		println("void " + qcname + "::" + CName.INSTANCE_INIT + "()");
		println("{");
		print(init.toString());
		println("}");
		println();
	}

	private void addInit(boolean isStatic, Block body,
			VariableDeclarationFragment field) {
		PrintWriter old = out;
		if (isStatic) {
			if (clinit == null) {
				clinit = new StringWriter();
			}

			out = new PrintWriter(clinit);
		} else {
			if (init == null) {
				init = new StringWriter();
			}

			out = new PrintWriter(init);
		}

		indent++;
		if (body != null) {
			printi();
			body.accept(this);
			println();
		} else {
			assert (field != null);
			printi();
			field.getName().accept(this);
			print(" = ");
			field.getInitializer().accept(this);
			println(";");

			ITypeBinding ib = field.getInitializer().resolveTypeBinding();
			if (!ib.isEqualTo(field.resolveBinding().getType())) {
				hardDep(ib);
			}
		}

		indent--;

		out.close();
		out = old;
	}

	private void printCtors() {
		if (!TypeUtil.isClassLike(type) || type.isAnonymous()) {
			return;
		}

		boolean hasEmpty = false;
		for (MethodDeclaration md : constructors) {
			locals.add(new ArrayList<String>());
			printi(qcname + "::" + name + "(");

			String sep = TransformUtil.printNestedParams(out, type, closures);

			if (!md.parameters().isEmpty()) {
				print(sep);

				visitAllCSV(md.parameters(), false);
			} else {
				hasEmpty = true;
			}

			println(") " + TransformUtil.throwsDecl(md.thrownExceptions()));

			indent++;
			printDefaultInitCall();
			indent--;

			println("{");
			indent++;

			printi(CName.CTOR + "(");

			printNames(md.parameters());

			println(");");

			indent--;
			println("}");
			println();
			locals.remove(locals.size() - 1);
		}

		if (!hasEmpty) {
			printEmptyCtor();
		}
	}

	private void printDefaultInitCall() {
		String sep;
		printi(": " + name + "(");
		sep = "";
		if (TransformUtil.hasOuterThis(type)) {
			print(TransformUtil.outerThisName(type));
			sep = ", ";
		}

		if (closures != null) {
			for (IVariableBinding closure : closures) {
				print(sep + CName.of(closure));
				sep = ", ";
			}
		}

		println(sep + "*static_cast< ::" + CName.DEFAULT_INIT_TAG + "* >(0))");
	}

	private void printAnonCtors() {
		if (!type.isAnonymous()) {
			return;
		}

		// In reality, we should only synthesize the constructor actually being
		// used, but...
		List<IMethodBinding> anonCtors = new ArrayList<IMethodBinding>();
		Header.getAnonCtors(type, anonCtors);
		for (IMethodBinding mb : anonCtors) {
			locals.add(new ArrayList<String>());

			printi(qcname + "::" + name + "(");

			String sep = TransformUtil.printNestedParams(out, type, closures);

			if (mb.getParameterTypes().length > 0) {
				out.print(sep);
				TransformUtil.printParams(out, type, mb, false, deps);
			}

			println(")");
			printAnonCtorBody();

			locals.remove(locals.size() - 1);
		}

		if (anonCtors.isEmpty()) {
			locals.add(new ArrayList<String>());

			printi(qcname + "::" + name + "(");

			TransformUtil.printNestedParams(out, type, closures);

			println(")");
			printAnonCtorBody();

			locals.remove(locals.size() - 1);
		}
	}

	private void printAnonCtorBody() {
		indent++;
		printFieldInit(": ");
		indent--;

		println("{");
		indent++;

		printlni(CName.STATIC_INIT + "();");

		if (init != null) {
			printlni(CName.INSTANCE_INIT + "();");
		}

		indent--;
		println("}");
		println();
	}

	private void printDefaultInitCtor() {
		if (!TypeUtil.isClassLike(type) || type.isAnonymous()) {
			return;
		}

		print(qcname + "::" + name + "(");
		print(TransformUtil.printNestedParams(out, type, closures));
		println("const ::" + CName.DEFAULT_INIT_TAG + "&)");

		indent++;
		printFieldInit(": ");
		indent--;

		println("{");
		println(i1 + CName.STATIC_INIT + "();");
		println("}");
		println();

	}

	private void printEmptyCtor() {
		if (type.isAnonymous()) {
			return;
		}

		print(qcname + "::" + name + "(");
		TransformUtil.printNestedParams(out, type, closures);
		println(")");
		indent++;
		printDefaultInitCall();
		indent--;
		println("{");
		indent++;
		printlni(CName.CTOR + "();");
		indent--;
		println("}");
		println();

		println("void " + qcname + "::" + CName.CTOR + "()");
		println("{");
		if (type.getSuperclass() != null) {
			println(i1 + "super::" + CName.CTOR + "();");
		}

		if (init != null) {
			printlni(i1 + CName.INSTANCE_INIT + "();");
		}

		println("}");
		println();
	}

	private void printFieldInit(String sep) {
		ITypeBinding sb = type.getSuperclass();
		if (sb != null && TransformUtil.hasOuterThis(sb)) {
			print(i1 + sep);
			print("super(");
			String sepx = "";
			for (ITypeBinding tb = type; tb.getDeclaringClass() != null; tb = tb
					.getDeclaringClass().getErasure()) {
				print(sepx);
				sepx = "->";
				hardDep(tb.getDeclaringClass());
				print(TransformUtil.outerThisName(tb));
				if (tb.getDeclaringClass()
						.getErasure()
						.isSubTypeCompatible(
								sb.getDeclaringClass().getErasure())) {
					break;
				}

			}

			println(")");
			sep = ", ";
		}

		if (TransformUtil.hasOuterThis(type)
				&& (sb == null || sb.getDeclaringClass() == null || !type
						.getDeclaringClass().getErasure()
						.isEqualTo(sb.getDeclaringClass().getErasure()))) {
			print(i1 + sep);
			hardDep(type.getDeclaringClass());
			printInit(TransformUtil.outerThisName(type));
			sep = ", ";
		}

		for (VariableDeclarationFragment vd : fields) {
			print(i1 + sep);
			vd.getName().accept(this);

			print("(");

			if (vd.getInitializer() != null
					&& TransformUtil.constantValue(vd) instanceof String) {
				vd.getInitializer().accept(this);
			}

			println(")");

			sep = ", ";
		}

		if (closures != null) {
			for (IVariableBinding closure : closures) {
				printi(sep);
				printInit(CName.of(closure));
				sep = ", ";
			}
		}
	}

	private void printInit(String n) {
		println(n + "(" + n + ")");
	}

	private static class NodeInfo {
		public final ASTNode node;
		public final String str;

		public NodeInfo(ASTNode node, String str) {
			this.node = node;
			this.str = str;
		}
	}

	private final List<NodeInfo> visits = new ArrayList<NodeInfo>();

	@Override
	public boolean preVisit2(ASTNode node) {
		for (Snippet snippet : ctx.snippets) {
			if (!snippet.node(ctx, this, node)) {
				return false;
			}
		}

		if (node instanceof Expression) {
			Expression expr = (Expression) node;
			ITypeBinding tb = expr.resolveTypeBinding();

			if ((expr.resolveBoxing() || expr.resolveUnboxing())
					&& checkBoxNesting(expr)) {

				if (expr.resolveBoxing()) {
					ITypeBinding tb2 = boxingType(expr);
					hardDep(tb2);
					print(CName.relative(tb2, type, true) + "::valueOf(");

					visits.add(new NodeInfo(node, ")"));
				} else if (expr.resolveUnboxing()) {
					if (TransformUtil.reverses.containsKey(tb
							.getQualifiedName())) {
						hardDep(tb);
						print("(");
						npc();
						visits.add(new NodeInfo(node, "))->"
								+ TransformUtil.reverses.get(tb
										.getQualifiedName()) + "Value()"));
					}
				}
			}

			if (node instanceof Name) {
				Name name = ((Name) node);
				IBinding b = name.resolveBinding();
				if (b instanceof IVariableBinding) {
					IVariableBinding vb = (IVariableBinding) b;
					castName(node, name, vb);
				}
			}
		}

		return super.preVisit2(node);
	}

	private void castName(ASTNode node, Name name, IVariableBinding vb) {
		if (!vb.isField()) {
			return;
		}

		if (!vb.getVariableDeclaration().getType().isTypeVariable()) {
			return;
		}

		if (!TransformUtil.variableErased(vb)) {
			return;
		}

		ASTNode parent = name.getParent();
		if (parent instanceof VariableDeclarationFragment
				&& node != ((VariableDeclarationFragment) parent)
						.getInitializer()) {
			return;
		}

		if (parent instanceof SingleVariableDeclaration
				&& node != ((SingleVariableDeclaration) parent)
						.getInitializer()) {
			return;
		}

		if (parent instanceof Name) {
			Name p = (Name) parent;
			if (p.resolveBinding().isEqualTo(vb)) {
				return;
			}
		}

		if (parent instanceof FieldAccess) {
			return;
		}

		if (parent instanceof Assignment) {
			if (((Assignment) parent).getLeftHandSide() == node) {
				return;
			}
		}

		if (parent instanceof EnumConstantDeclaration) {
			return;
		}

		javaCast(vb.getVariableDeclaration().getType().getErasure(),
				vb.getType());
		visits.add(new NodeInfo(node, ")"));
	}

	/**
	 * Java allows conversions on assignment when boxing byte, short, char -
	 * this method returns the type after conversion
	 */
	private ITypeBinding boxingType(Expression node) {
		ITypeBinding ret = null;
		ASTNode parent = node.getParent();
		if (parent instanceof Assignment) {
			ret = ((Assignment) parent).getLeftHandSide().resolveTypeBinding();
		}

		if (parent instanceof VariableDeclarationFragment) {
			ret = ((VariableDeclarationFragment) parent).resolveBinding()
					.getType();
		}

		if (parent instanceof SingleVariableDeclaration) {
			ret = ((SingleVariableDeclaration) parent).resolveBinding()
					.getType();
		}

		if (ret != null
				&& (TransformUtil.same(ret, Byte.class)
						|| TransformUtil.same(ret, Short.class) || TransformUtil
							.same(ret, Character.class))) {
			return ret;
		}

		return node.getAST().resolveWellKnownType(
				TransformUtil.primitives.get(node.resolveTypeBinding()
						.getName()));
	}

	private boolean checkBoxNesting(Expression expr) {
		ASTNode parent = expr.getParent();
		if (parent instanceof ParenthesizedExpression) {
			assert (((ParenthesizedExpression) parent).resolveBoxing() || ((ParenthesizedExpression) parent)
					.resolveUnboxing());
			return false;
		}

		if (parent instanceof Name) {
			return false;
		}

		if (parent instanceof MethodInvocation) {
			MethodInvocation mi = (MethodInvocation) parent;
			if (mi.getExpression() == expr || mi.getName() == expr) {
				return false;
			}
		}

		if (parent instanceof FieldAccess
				&& ((FieldAccess) parent).getName() == expr) {
			return false;
		}

		return true;
	}

	@Override
	public void postVisit(ASTNode node) {
		if (visits.isEmpty()) {
			return;
		}

		NodeInfo last = visits.get(visits.size() - 1);

		if (!last.node.equals(node)) {
			return;
		}

		print(last.str);
		visits.remove(visits.size() - 1);
	}

	@Override
	public boolean visit(AnnotationTypeDeclaration node) {
		ITypeBinding tb = node.resolveBinding();
		ImplWriter iw = new ImplWriter(root, ctx, tb, unitInfo);
		try {
			iw.write(node);
		} catch (Exception e) {
			throw new Error(e);
		}

		if (tb.isLocal()) {
			localTypes.put(tb, iw);
		}

		return false;
	}

	@Override
	public boolean visit(AnnotationTypeMemberDeclaration node) {
		return false;
	}

	@Override
	public boolean visit(AnonymousClassDeclaration node) {
		ITypeBinding tb = node.resolveBinding();
		ImplWriter iw = new ImplWriter(root, ctx, tb, unitInfo);
		try {
			iw.write(node);
		} catch (Exception e) {
			throw new Error(e);
		}

		localTypes.put(tb, iw);

		if (iw.closures != null && closures != null) {
			for (IVariableBinding vb : iw.closures) {
				if (!vb.getDeclaringMethod().getDeclaringClass()
						.isEqualTo(type)) {
					closures.add(vb);
				}
			}
		}

		return false;
	}

	@Override
	public boolean visit(ArrayAccess node) {
		hardDep(node.getArray().resolveTypeBinding());

		print("(*");
		node.getArray().accept(this);
		print(")[");
		node.getIndex().accept(this);
		print("]");

		return false;
	}

	@Override
	public boolean visit(ArrayCreation node) {
		ITypeBinding tb = node.getType().resolveBinding();
		hardDep(tb);
		print("(new " + CName.relative(tb, type, true));

		for (Iterator<Expression> it = node.dimensions().iterator(); it
				.hasNext();) {
			print("(");
			Expression e = it.next();
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
			print(CName.qualified(at, true));
			hardDep(at);
		}

		print("({");
		if (!node.expressions().isEmpty()) {
			String s = "";
			ITypeBinding ct = node.resolveTypeBinding().getComponentType();
			for (Expression e : (List<Expression>) node.expressions()) {
				hardDep(e.resolveTypeBinding());
				if (node.expressions().size() > 1) {
					println();
					printi(TransformUtil.indent(1));
				}

				print(s);
				s = ", ";
				arrayInitCast(ct, e);
			}
		}

		if (node.expressions().size() > 1) {
			println();
			printi();
		}

		print("})");

		if (!(node.getParent() instanceof ArrayCreation)) {
			print(")");
		}

		return false;
	}

	private void arrayInitCast(ITypeBinding ct, Expression e) {
		boolean cast = e instanceof NullLiteral
				|| !ct.isEqualTo(e.resolveTypeBinding());
		if (cast) {
			hardDep(e.resolveTypeBinding());
			print("static_cast< " + CName.relative(ct, type, true)
					+ TransformUtil.ref(ct) + " >(");
		}
		e.accept(this);
		if (cast) {
			print(")");
		}
	}

	@Override
	public boolean visit(Assignment node) {
		Expression lhs = node.getLeftHandSide();
		ITypeBinding lht = lhs.resolveTypeBinding();
		Expression rhs = node.getRightHandSide();

		hardDep(rhs.resolveTypeBinding());

		if (TransformUtil.same(lht, String.class)
				&& node.getOperator() == Operator.PLUS_ASSIGN) {

			if (lhs instanceof ArrayAccess) {
				ArrayAccess aa = (ArrayAccess) lhs;
				hardDep(aa.getArray().resolveTypeBinding());
				aa.getArray().accept(this);
				print("->set(");
				aa.getIndex().accept(this);
				print(", ");

			} else {
				lhs.accept(this);
				print(" = ");
			}

			print("(new ::java::lang::StringBuilder(");
			lhs.accept(this);
			print("))->append(");
			castNull(rhs);
			print(")->toString()");

			if (lhs instanceof ArrayAccess) {
				print(")");
			}
			hardDep(ctx.resolve(StringBuilder.class));

			return false;
		}

		if (node.getOperator() == Operator.RIGHT_SHIFT_UNSIGNED_ASSIGN) {
			lhs.accept(this);
			print(" = ");
			if (lht.getName().equals("long")) {
				print("static_cast<uint64_t>(");
			} else {
				print("static_cast<uint32_t>(");
			}
			lhs.accept(this);
			print(") >> ");
			rhs.accept(this);

			return false;
		}

		if (node.getOperator().equals(Operator.REMAINDER_ASSIGN)) {
			if (lht.getName().equals("float") || lht.getName().equals("double")) {
				fmod = true;
				lhs.accept(this);
				print(" = ");
				print("std::fmod(");
				lhs.accept(this);
				print(", ");
				lhs.accept(this);
				print(")");

				return false;
			}
		}

		if (node.getOperator() == Operator.ASSIGN
				&& lhs instanceof ArrayAccess
				&& !((ArrayAccess) lhs).getArray().resolveTypeBinding()
						.getComponentType().isPrimitive()) {
			ArrayAccess aa = (ArrayAccess) lhs;
			hardDep(aa.getArray().resolveTypeBinding());
			aa.getArray().accept(this);
			print("->set(");
			aa.getIndex().accept(this);
			print(", ");
			rhs.accept(this);
			print(")");
			return false;
		}

		lhs.accept(this);

		print(" " + node.getOperator() + " ");

		rhs.accept(this);

		return false;
	}

	private List<Class<?>> handledBlocks = new ArrayList<Class<?>>(
			Arrays.asList(Block.class, CatchClause.class, DoStatement.class,
					EnhancedForStatement.class, ForStatement.class,
					IfStatement.class, Initializer.class,
					LabeledStatement.class, MethodDeclaration.class,
					SynchronizedStatement.class, SwitchStatement.class,
					TryStatement.class, WhileStatement.class));

	@Override
	public boolean visit(Block node) {
		ASTNode parent = node.getParent();
		if (!handledBlocks.contains(parent.getClass())) {
			System.out.println("Skipped " + parent.getClass() + " block");
			return false;
		}

		locals.add(new ArrayList<String>());
		println("{");

		indent++;

		if (parent instanceof MethodDeclaration
				&& Modifier.isStatic(((MethodDeclaration) parent)
						.getModifiers())) {
			printlni("clinit();");
		}

		visitAll(node.statements());

		indent--;
		printi("}");

		locals.remove(locals.size() - 1);
		return false;
	}

	@Override
	public boolean visit(BreakStatement node) {
		if (node.getLabel() != null) {
			printi("goto ");
			printLabelName(node.getLabel());
			print("_break");
		} else if (!enumSwitches.isEmpty()
				&& breakEnclosingStatement(node) == enumSwitches
						.get(enumSwitches.size() - 1)) {
			printi("goto " + enumSwitchLabel() + ";");

		} else {
			printi("break");
		}

		println(";");
		return false;
	}

	private boolean isLoopStatement(ASTNode node) {
		return node instanceof ForStatement || node instanceof WhileStatement
				|| node instanceof DoStatement;
	}

	private Statement breakEnclosingStatement(ASTNode node) {
		assert (node != null); // Break without enclosing statement?
		if (node instanceof SwitchStatement || isLoopStatement(node)) {
			return (Statement) node;
		}

		return breakEnclosingStatement(node.getParent());
	}

	@Override
	public boolean visit(CastExpression node) {
		ITypeBinding target = node.getType().resolveBinding();
		ITypeBinding source = node.getExpression().resolveTypeBinding();

		if (target.isPrimitive() || node.getExpression() instanceof NullLiteral) {
			print("static_cast< " + CName.relative(target, type, true)
					+ TransformUtil.ref(target) + " >(");
		} else {
			javaCast(source, target);
		}

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
		print("(new ");

		ITypeBinding tb = node.getType().resolveBinding();

		if (node.getAnonymousClassDeclaration() != null) {
			tb = node.getAnonymousClassDeclaration().resolveBinding();
			print(CName.of(tb));
			node.getAnonymousClassDeclaration().accept(this);
		} else {
			print(TransformUtil.typeArguments(node.typeArguments()));

			print(CName.relative(tb, type, true));
		}

		hardDep(tb);

		consArgs(node.getExpression(), node.arguments(),
				node.resolveConstructorBinding(), tb);

		print(")");

		return false;
	}

	private void consArgs(Expression expression, List<Expression> arguments,
			IMethodBinding mb, ITypeBinding tb) {
		print("(");

		String sep = "";
		if (expression != null) {
			expression.accept(this);
			sep = ", ";
		} else if (TransformUtil.hasOuterThis(tb)) {
			ITypeBinding dce = tb.getDeclaringClass().getErasure();
			if (type.getErasure().isSubTypeCompatible(dce.getErasure())) {
				print("this");
			} else {
				String sep2 = "";
				for (ITypeBinding x = type.getErasure(); x.getDeclaringClass() != null
						&& !x.isSubTypeCompatible(dce); x = x
						.getDeclaringClass().getErasure()) {
					hardDep(x.getDeclaringClass());

					print(sep2 + TransformUtil.outerThisName(x));
					sep2 = "->";
				}
			}
			sep = ", ";
		}

		if (localTypes.containsKey(tb)) {
			ImplWriter iw = localTypes.get(tb);
			for (IVariableBinding closure : iw.closures) {
				print(sep + CName.of(closure));
				sep = ", ";
			}
		}

		if (!arguments.isEmpty()) {
			print(sep);
			callArgs(mb, arguments, false);
		}

		print(")");
	}

	@Override
	public boolean visit(ConditionalExpression node) {
		ITypeBinding tb = node.resolveTypeBinding();
		node.getExpression().accept(this);
		print(" ? ");
		cast(node.getThenExpression(), tb, true);
		print(" : ");
		cast(node.getElseExpression(), tb, true);
		return false;
	}

	@Override
	public boolean visit(ConstructorInvocation node) {
		printi(TransformUtil.typeArguments(node.typeArguments()));

		print(CName.CTOR);

		callArgs(node.resolveConstructorBinding(), node.arguments(), true);

		println(";");
		return false;
	}

	@Override
	public boolean visit(ContinueStatement node) {
		if (node.getLabel() != null) {
			Statement loop = outerLoop(node);
			if (isLabeled(loop, node.getLabel())) {
				printlni("continue;");
			} else {
				printi("{ ");
				printLabelName(node.getLabel());
				println("_continue = true; break; }");
			}
		} else {
			printlni("continue;");
		}

		return false;
	}

	private void printContinueVar(LabeledStatement s) {
		printi("bool ");
		printLabelName(s.getLabel());
		println("_continue = false;");
	}

	private static final List<Class<? extends Statement>> loopStatements = Arrays
			.asList(DoStatement.class, EnhancedForStatement.class,
					ForStatement.class, WhileStatement.class);

	private static boolean isLabeled(Statement s) {
		return s.getParent() instanceof LabeledStatement;
	}

	private static boolean isLabeled(Statement s, SimpleName label) {
		return isLabeled(s)
				&& ((LabeledStatement) s.getParent()).getLabel()
						.getIdentifier().equals(label.getIdentifier());
	}

	private static Statement outerLoop(ASTNode node) {
		for (ASTNode n = node; n != null; n = n.getParent()) {
			if (loopStatements.contains(n.getClass())) {
				return (Statement) n;
			}
		}

		throw new Error("Expected a loop");
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
		ITypeBinding eb = node.getExpression().resolveTypeBinding();
		hardDep(eb);
		deps.setNpc();
		if (eb.isArray()) {
			printlni("{");
			indent++;
			printi("auto _a = ");
			npc();
			node.getExpression().accept(this);
			println(");");
			printlni("for(int _i = 0; _i < _a->length; ++_i) {");
			indent++;
			printi();
			node.getParameter().accept(this);
			println(" = (*_a)[_i];");
			printi();
			handleLoopBody(node, node.getBody());
			indent--;
			printlni("}");
			indent--;
			printlni("}");
		} else {
			printi("for (auto _i = ");
			npc();
			node.getExpression().accept(this);
			println(")->iterator(); _i->hasNext(); ) {");
			indent++;
			printi();
			node.getParameter().accept(this);
			print(" = ");
			ITypeBinding tb = node.getParameter().getType().resolveBinding();

			if (tb.isPrimitive()) {
				ITypeBinding tbb = node.getAST().resolveWellKnownType(
						TransformUtil.primitives.get(tb.getName()));
				hardDep(tbb);
				npc();
				javaCast(ctx.resolve(Object.class), tbb);
				println("_i->next()))->" + tb.getName() + "Value();");
			} else {
				javaCast(ctx.resolve(Object.class), tb);
				println("_i->next());");
			}

			printi();
			handleLoopBody(node, node.getBody());

			indent--;
			printlni("}");

			hardDep(ctx.resolve(Iterator.class));
		}

		return false;
	}

	@Override
	public boolean visit(EnumConstantDeclaration node) {
		print(qcname + " *" + qcname + "::");

		node.getName().accept(this);

		ITypeBinding tb = node.getAnonymousClassDeclaration() == null ? type
				: node.getAnonymousClassDeclaration().resolveBinding();

		hardDep(tb);
		print(" = new " + CName.qualified(tb, true));

		consArgs(null, node.arguments(), node.resolveConstructorBinding(), tb);

		println(";");

		if (node.getAnonymousClassDeclaration() != null) {
			node.getAnonymousClassDeclaration().accept(this);
		}

		return false;
	}

	@Override
	public boolean visit(EnumDeclaration node) {
		ITypeBinding tb = node.resolveBinding();
		ImplWriter iw = new ImplWriter(root, ctx, tb, unitInfo);
		try {
			iw.write(node);
		} catch (Exception e) {
			throw new Error(e);
		}

		if (tb.isLocal()) {
			localTypes.put(tb, iw);
		}

		return false;
	}

	@Override
	public boolean visit(FieldAccess node) {
		ITypeBinding tb = node.resolveTypeBinding();
		ITypeBinding tbe = node.resolveFieldBinding().getVariableDeclaration()
				.getType().getErasure();
		ASTNode parent = node.getParent();
		boolean cast = TransformUtil.variableErased(node.resolveFieldBinding());
		if (cast && parent instanceof Assignment) {
			if (((Assignment) parent).getLeftHandSide() == node) {
				cast = false;
			}
		}

		if (cast) {
			javaCast(tbe, tb);
		}

		npc();
		node.getExpression().accept(this);
		hardDep(node.getExpression().resolveTypeBinding());
		print(")->");
		node.getName().accept(this);
		if (cast) {
			print(")");
		}

		return false;
	}

	@Override
	public boolean visit(FieldDeclaration node) {
		Iterable<VariableDeclarationFragment> fragments = node.fragments();

		boolean isStatic = Modifier.isStatic(node.getModifiers());
		for (VariableDeclarationFragment f : fragments) {
			Object cv = TransformUtil.constantValue(f);

			if (isStatic) {
				if (cv instanceof String) {
					consts.add(f);
				} else if (cv == null && f.getInitializer() != null) {
					addInit(isStatic, null, f);
				}
			} else {
				if (f.getInitializer() != null && cv == null) {
					addInit(isStatic, null, f);
				}

				if (cv instanceof String || cv == null) {
					fields.add(f);
					continue;
				}
			}

			IVariableBinding vb = f.resolveBinding();
			boolean asMethod = TransformUtil.asMethod(vb);

			if (asMethod) {
				ITypeBinding tb = vb.getType();
				print(CName.qualified(tb, true) + " " + TransformUtil.ref(tb));

				println("&" + qcname + "::" + CName.of(vb) + "()");
				printlni("{");
				indent++;
				printlni("clinit();");
				printlni("return " + CName.of(vb) + "_;");
				indent--;
				println("}");
			}

			printi(TransformUtil.fieldModifiers(type, node.getModifiers(),
					false, cv != null && !(cv instanceof String)));

			ITypeBinding tb = node.getType().resolveBinding();
			tb = f.getExtraDimensions() > 0 ? tb.createArrayType(f
					.getExtraDimensions()) : tb;
			print(CName.qualified(tb, true));

			print(" " + TransformUtil.ref(tb));

			print(qcname + "::");

			if (asMethod) {
				println(CName.of(vb) + "_;");
			} else {
				f.getName().accept(this);
				println(";");
			}

			println();
		}

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

		handleLoopBody(node, node.getBody());

		return false;
	}

	private boolean skipIndent = false;
	private boolean fmod;

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
		Expression left = node.getLeftOperand();
		Expression right = node.getRightOperand();

		ITypeBinding lt = left.resolveTypeBinding();
		ITypeBinding rt = right.resolveTypeBinding();

		if (TransformUtil.same(tb, String.class)) {
			print("(new ::java::lang::StringBuilder())->append(");

			castNull(left);

			print(")->append(");

			castNull(right);

			print(")");

			indent++;
			for (Expression e : extendedOperands) {
				println();
				printi("->append(");
				castNull(e);
				print(")");
			}
			indent--;

			print("->toString()");

			hardDep(ctx.resolve(StringBuilder.class));

			return false;
		}

		if (node.getOperator().equals(InfixExpression.Operator.REMAINDER)) {
			if (tb.getName().equals("float") || tb.getName().equals("double")) {
				fmod = true;
				print("std::fmod(");
				cast(left, tb, true);
				print(", ");
				cast(right, tb, true);
				print(")");

				return false;
			}
		}

		ITypeBinding common = null;
		if (!lt.isEqualTo(rt)) {
			// If we have pointer compares, we need the complete type
			hardDep(lt);
			hardDep(rt);
			if (!lt.isPrimitive()
					&& !rt.isPrimitive()
					&& (node.getOperator().equals(
							InfixExpression.Operator.EQUALS) || node
							.getOperator().equals(
									InfixExpression.Operator.NOT_EQUALS))) {
				common = TypeUtil.commonBase(lt, rt, ctx.resolve(Object.class));
			}
		}

		boolean cast = false;
		if (node.getOperator().equals(
				InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED)) {
			print("static_cast<" + CName.of(lt) + ">(");
			cast = true;
			if (lt.getName().equals("long")) {
				print("static_cast<uint64_t>(");
			} else {
				print("static_cast<uint32_t>(");
			}
			left.accept(this);
			print(") >>");
		} else {
			staticCast(left, lt, common);
			print(" "); // for cases like x= i - -1; or x= i++ + ++i;

			print(node.getOperator().toString());
		}

		print(" ");

		staticCast(right, rt, common);

		if (!extendedOperands.isEmpty()) {
			print(" ");

			for (Expression e : extendedOperands) {
				print(node.getOperator() + " ");
				e.accept(this);
				hardDep(e.resolveTypeBinding());
			}
		}

		if (cast) {
			print(")");
		}
		return false;
	}

	private void staticCast(Expression left, ITypeBinding lt,
			ITypeBinding common) {
		if (common != null && !lt.isEqualTo(common) && !lt.isNullType()) {
			print("static_cast< " + CName.relative(common, type, true)
					+ TransformUtil.ref(common) + " >(");
			left.accept(this);
			print(")");
		} else {
			left.accept(this);
		}
	}

	private void castNull(Expression left) {
		if (left.resolveTypeBinding().isNullType()) {
			print("static_cast< ::java::lang::Object* >(");
			left.accept(this);
			print(")");
		} else {
			left.accept(this);
		}
	}

	@Override
	public boolean visit(Initializer node) {
		addInit(Modifier.isStatic(node.getModifiers()), node.getBody(), null);

		return false;
	}

	@Override
	public boolean visit(InstanceofExpression node) {
		ITypeBinding lb = node.getLeftOperand().resolveTypeBinding();
		ITypeBinding rb = node.getRightOperand().resolveBinding();

		hardDep(lb);
		hardDep(rb);
		print("dynamic_cast< " + CName.relative(rb, type, true) + "* >(");
		node.getLeftOperand().accept(this);
		print(") != nullptr");
		return false;
	}

	private List<LabeledStatement> labels = new ArrayList<LabeledStatement>();
	private Map<String, Integer> labelNames = new TreeMap<String, Integer>();

	@Override
	public boolean visit(LabeledStatement node) {
		labels.add(node);
		String id = node.getLabel().getIdentifier();
		if (labelNames.containsKey(id)) {
			labelNames.put(id, labelNames.get(id) + 1);
		} else {
			labelNames.put(id, 0);
		}

		node.getBody().accept(this);

		labels.remove(labels.size() - 1);

		// We put a break label after the body as this is where break <label>
		// statements end up
		println();
		printLabelName(node.getLabel());
		println("_break:;");

		return false;
	}

	private void printLabelName(SimpleName n) {
		n.accept(this);
		int i = labelNames.get(n.getIdentifier());
		print("" + i);
	}

	private void handleLoopBody(Statement loop, Statement body) {
		if (loop.getParent() instanceof LabeledStatement) {
			LabeledStatement ls = (LabeledStatement) loop.getParent();
			println("{");
			indent++;

			printContinueVar(ls);

			if (body instanceof Block) {
				visitAll(((Block) body).statements());
			} else {
				body.accept(this);
			}

			println();

			handleLoopEnd(loop);
			indent--;
			printlni("}");
		} else {
			if (body instanceof Block) {
				body.accept(this);
			} else {
				println();
				indent++;
				printi();
				body.accept(this);
				indent--;
			}

			println();
		}
	}

	private void handleLoopEnd(Statement loop) {
		for (LabeledStatement ls : labels) {
			if (!loopStatements.contains(ls.getBody())) {
				continue;
			}

			printi("if(");
			printLabelName(ls.getLabel());
			print("_continue) ");
			if (isLabeled(loop, ls.getLabel())) {
				println("continue;");
			} else {
				println("break;");
			}
		}
	}

	@Override
	public boolean visit(MethodDeclaration node) {
		if (node.getBody() == null) {
			hasNatives |= Modifier.isNative(node.getModifiers());
			return false;
		}

		locals.add(new ArrayList<String>());

		IMethodBinding mb = node.resolveBinding();
		if (TransformUtil.isMain(mb)) {
			ctx.main(type);
		}

		printi(TransformUtil.typeParameters(node.typeParameters()));

		if (node.isConstructor()) {
			constructors.add(node);

			printi("void " + qcname + "::" + CName.CTOR);
		} else {
			ITypeBinding rt = TransformUtil.returnType(node);
			softDep(rt);
			print(CName.qualified(rt, true) + " " + TransformUtil.ref(rt));

			print(qcname + "::");

			node.getName().accept(this);
		}

		visitAllCSV(node.parameters(), true);

		println(TransformUtil.throwsDecl(node.thrownExceptions()));

		if (node.isConstructor()) {
			println("{");
			indent++;
			Statement first = null;
			List<Statement> statements = node.getBody().statements();
			if (!statements.isEmpty()) {
				first = statements.get(0);
			}

			if (!(first instanceof ConstructorInvocation)) {
				if (!(first instanceof SuperConstructorInvocation)) {
					if (type.getSuperclass() != null) {
						printlni("super::" + CName.CTOR + "();");
					}
				} else {
					first.accept(this);
				}

				if (init != null) {
					printlni(CName.INSTANCE_INIT + "();");
				}
			}

			visitAll(first instanceof SuperConstructorInvocation ? statements
					.subList(1, statements.size()) : statements);
			indent--;
			print("}");
		} else {
			node.getBody().accept(this);
		}

		locals.remove(locals.size() - 1);

		println();
		println();

		TransformUtil.defineBridge(out, type, mb, deps);

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
		IMethodBinding b = node.resolveMethodBinding();
		boolean erased = (b.getMethodDeclaration().getReturnType()
				.isTypeVariable() || b.getMethodDeclaration().getReturnType()
				.isArray()
				&& b.getMethodDeclaration().getReturnType().getElementType()
						.isTypeVariable())
				&& !(node.getParent() instanceof ExpressionStatement);

		if (erased) {
			javaCast(b.getMethodDeclaration().getReturnType().getErasure(),
					b.getReturnType());
		}

		Expression expr = node.getExpression();
		if (expr != null) {
			// This cast is need for multiply bounded generic types (<T extends
			// A & B>)
			ITypeBinding etb = expr.resolveTypeBinding().getErasure();
			boolean castExpr = !etb.isSubTypeCompatible(b.getDeclaringClass()
					.getErasure());
			boolean isType = expr instanceof Name
					&& ((Name) expr).resolveBinding() instanceof ITypeBinding;

			if (castExpr) {
				javaCast(etb, b.getDeclaringClass());
			}

			if (!isType) {
				npc();
			}

			expr.accept(this);

			if (!isType) {
				print(")");
			}

			if (castExpr) {
				print(")");
			}

			if (isType) {
				print("::");
			} else {
				print("->");
			}

			hardDep(etb);
		} else {
			String bname = CName.of(b);
			boolean found = false;
			for (List<String> l : locals) {
				if (l.contains(bname)) {
					found = true;
					break;
				}
			}

			if (!b.getDeclaringClass().isEqualTo(type)) {
				for (IVariableBinding vb : type.getDeclaredFields()) {
					if (bname.equals(CName.of(vb))) {
						found = true;
					}
				}
			}

			if (found) {
				if (TransformUtil.isStatic(b)) {
					print(CName.of(b.getDeclaringClass()) + "::");
				} else {
					print("this->");
					if (!b.getDeclaringClass().isEqualTo(type)) {
						print(CName.of(b.getDeclaringClass()) + "::");
					}
				}
			}
		}

		print(TransformUtil.typeArguments(node.typeArguments()));

		node.getName().accept(this);

		callArgs(b, node.arguments(), true);

		if (erased) {
			print(")");
		}

		return false;
	}

	private void callArgs(IMethodBinding b, List<Expression> arguments,
			boolean parens) {
		if (parens) {
			print("(");
		}
		String s = "";
		boolean isVarArg = false;
		b = b.getMethodDeclaration();

		boolean hasOverloads = isOverloaded(b);
		ITypeBinding[] paramTypes = b.getParameterTypes();
		for (int i = 0; i < arguments.size(); ++i) {
			print(s);
			s = ", ";
			if (b.isVarargs() && i == paramTypes.length - 1) {
				ITypeBinding tb = paramTypes[paramTypes.length - 1]
						.getErasure();
				ITypeBinding ab = arguments.get(i).resolveTypeBinding();

				if (!ab.isAssignmentCompatible(tb) || i != arguments.size() - 1) {
					hardDep(tb);
					print("new " + CName.relative(tb, type, true) + "({");
					isVarArg = true;
				}
			}

			ITypeBinding pb;
			if (b.isVarargs() && i >= paramTypes.length - 1) {
				pb = paramTypes[paramTypes.length - 1];
				if (isVarArg) {
					pb = pb.getComponentType();
				}
			} else {
				pb = paramTypes[i];
			}

			Expression argument = arguments.get(i);
			if (isVarArg) {
				arrayInitCast(paramTypes[paramTypes.length - 1].getErasure()
						.getComponentType(), argument);
			} else {
				cast(argument, pb, hasOverloads);
			}
			if (isVarArg && i == arguments.size() - 1
					&& i >= paramTypes.length - 1) {
				print("})");
			}
		}

		if (b.isVarargs() && arguments.size() < paramTypes.length) {
			if (arguments.size() > 0) {
				print(", ");
			}

			ITypeBinding tb = paramTypes[paramTypes.length - 1];
			hardDep(tb);
			print("new " + CName.relative(tb, type, true) + "()");
		}

		if (parens) {
			print(")");
		}
	}

	private boolean isOverloaded(IMethodBinding b) {
		Collection<IMethodBinding> methods = TypeUtil.methods(TypeUtil.types(
				b.getDeclaringClass(), ctx.resolve(Object.class)), TypeUtil
				.named(b.getName()));
		if (methods.size() < 2) {
			return false;
		}

		for (IMethodBinding mb : methods) {
			if (!mb.isEqualTo(b)
					&& mb.getParameterTypes().length == b.getParameterTypes().length) {
				return true;
			}
		}

		return false;
	}

	private void javaCast(ITypeBinding source, ITypeBinding target) {
		hardDep(source);
		hardDep(target);
		deps.setJavaCast();
		print(CName.JAVA_CAST + "< " + CName.relative(target, type, true)
				+ "* >(");
	}

	private void cast(Expression argument, ITypeBinding pb, boolean hasOverloads) {
		ITypeBinding tb = argument.resolveTypeBinding();
		if (!tb.isEqualTo(pb)
				&& !(argument.resolveBoxing() || argument.resolveUnboxing())) {
			// Java has different implicit cast rules when resolving overloads
			// i e int -> double promotion, int vs pointer
			hardDep(tb);
			if (hasOverloads) {
				print("static_cast< " + CName.relative(pb, type, true)
						+ TransformUtil.ref(pb) + " >(");
				argument.accept(this);
				print(")");
			} else {
				argument.accept(this);
			}
		} else {
			argument.accept(this);
		}
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
			hardDep(node.getExpression().resolveTypeBinding());
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

			if (isClosure(node, vb)) {
				closures.add(vb);
			}
		}

		boolean ret = super.visit(node);

		if (b instanceof IVariableBinding
				&& TransformUtil.asMethod((IVariableBinding) b)) {
			if (((IVariableBinding) b).getDeclaringClass().isEqualTo(type)) {
				print("_");
			} else {
				print("()");
			}
		}

		return ret;
	}

	private boolean isClosure(SimpleName node, IVariableBinding vb) {
		if (vb.isField()) {
			return false;
		}

		if (vb.getDeclaringMethod() == null) {
			IJavaElement je = vb.getJavaElement().getAncestor(
					IJavaElement.INITIALIZER);

			// Could be a variable in an initializer block
			if (je == null
					|| (((IType) je.getAncestor(IJavaElement.TYPE))
							.getFullyQualifiedName().equals(((IType) type
							.getJavaElement()).getFullyQualifiedName()))) {
				return false;
			}
		}

		if (!Modifier.isFinal(vb.getModifiers())) {
			return false;
		}
		VariableDeclarationFragment vdf = initializer(node);
		if (vdf != null) {
		} else if (vb.getDeclaringMethod() != null) {
			IMethodBinding pmb = parentMethod(node);
			if (pmb.isEqualTo(vb.getDeclaringMethod())) {
				return false;
			}
		}

		return true;
	}

	private VariableDeclarationFragment initializer(ASTNode node) {
		for (ASTNode n = node; n != null; n = n.getParent()) {
			if (n.getParent() instanceof VariableDeclarationFragment) {
				VariableDeclarationFragment vdf = (VariableDeclarationFragment) n
						.getParent();
				if (type.isEqualTo(vdf.resolveBinding().getDeclaringClass())
						&& vdf.getInitializer() == n) {
					return vdf;
				}
			}
		}

		return null;
	}

	private static IMethodBinding parentMethod(ASTNode node) {
		for (ASTNode n = node; n != null; n = n.getParent()) {
			if (n instanceof MethodDeclaration) {
				return ((MethodDeclaration) n).resolveBinding();
			}
		}

		return null;
	}

	@Override
	public boolean visit(SingleVariableDeclaration node) {
		IVariableBinding vb = node.resolveBinding();
		if (!vb.isField()) {
			locals.get(locals.size() - 1).add(CName.of(vb));
		}

		ITypeBinding tb = node.getType().resolveBinding();
		if (node.getExtraDimensions() > 0) {
			tb = tb.createArrayType(node.getExtraDimensions());
		}

		if (node.isVarargs()) {
			tb = tb.createArrayType(1);
			print(CName.relative(tb, type, true));
			print("/*...*/");
		} else {
			print(CName.relative(tb, type, true));
		}

		print(" " + TransformUtil.ref(tb));

		if (node.getInitializer() != null) {
			print(TransformUtil.constVar(vb));
			node.getName().accept(this);
			hardDep(node.getInitializer().resolveTypeBinding());
			print(" = ");
			node.getInitializer().accept(this);
		} else {
			node.getName().accept(this);
		}

		return false;
	}

	@Override
	public boolean visit(SuperConstructorInvocation node) {
		// We skip node.getExpression() here as that should have been taken
		// care of by the constructor invocation
		printi(TransformUtil.typeArguments(node.typeArguments()));

		print("super::" + CName.CTOR);

		callArgs(node.resolveConstructorBinding(), node.arguments(), true);

		println(";");
		return false;
	}

	@Override
	public boolean visit(SuperFieldAccess node) {
		// Qualification is handled in TransformWriter.visit(SimpleName)
		node.getName().accept(this);

		return false;
	}

	@Override
	public boolean visit(SuperMethodInvocation node) {
		IMethodBinding b = node.resolveMethodBinding();
		boolean erased = TransformUtil.returnErased(b);

		if (erased) {
			javaCast(b.getMethodDeclaration().getReturnType().getErasure(),
					b.getReturnType());
		}

		// Qualification is handled in TransformWriter.visit(SimpleName)
		print(TransformUtil.typeArguments(node.typeArguments()));

		node.getName().accept(this);

		callArgs(b, node.arguments(), true);

		if (erased) {
			print(")");
		}

		return false;
	}

	@Override
	public boolean visit(SwitchCase node) {
		printi();

		if (node.isDefault()) {
			print("default:");
		} else {
			print("case ");
			node.getExpression().accept(this);
			print(":");
		}

		return false;
	}

	@Override
	public boolean visit(SwitchStatement node) {
		List<Statement> statements = node.statements();
		List<VariableDeclarationStatement> vdss = declarations(statements);

		if (!vdss.isEmpty()) {
			printlni("{");
			indent++;

			for (VariableDeclarationStatement vds : vdss) {
				for (VariableDeclarationFragment fragment : (Iterable<VariableDeclarationFragment>) vds
						.fragments()) {
					ITypeBinding vdb = vds.getType().resolveBinding();
					ITypeBinding fb = fragment.getExtraDimensions() == 0 ? vdb
							: vdb.createArrayType(fragment.getExtraDimensions());
					hardDep(fb);

					printi(TransformUtil.variableModifiers(type,
							vds.getModifiers()));
					print(CName.relative(fb, type, true) + " ");
					print(TransformUtil.ref(fb));
					fragment.getName().accept(this);
					println(";");
				}
			}
		}

		if (node.getExpression().resolveTypeBinding().isEnum()) {
			enumSwitch(node, statements);
		} else {
			nativeSwitch(node, statements);
		}

		if (!vdss.isEmpty()) {
			indent--;
			printlni("}");
		}
		println();

		return false;
	}

	private final List<SwitchStatement> enumSwitches = new ArrayList<SwitchStatement>();
	private int enumSwitchCount = 0;

	private void enumSwitch(SwitchStatement node, List<Statement> statements) {
		// Enum constants as we translate them are not C++ constexpr:s so we
		// have to rewrite the switch to if:s
		printlni("{");
		indent++;
		printi("auto v = ");
		node.getExpression().accept(this);
		println(";");

		List<SwitchCase> cases = new ArrayList<SwitchCase>();
		List<SwitchCase> allCases = new ArrayList<SwitchCase>();
		enumSwitches.add(node);

		boolean wasCase = false;
		boolean indented = false;
		for (Statement s : statements) {
			if (s instanceof SwitchCase) {
				if (!wasCase && indented) {
					indent--;
					printlni("}");
					indented = false;
				}

				SwitchCase sc = (SwitchCase) s;
				if (!sc.isDefault()) {
					cases.add(sc);
				}

				allCases.add(sc);
				wasCase = true;
			} else {
				if (wasCase) {
					printi("if(");
					String sep = "";
					boolean hasDefault = allCases.get(allCases.size() - 1)
							.isDefault();

					if (hasDefault) {
						print("(");
					}
					for (SwitchCase x : cases) {
						print(sep);
						sep = " || ";
						print("(v == ");
						x.getExpression().accept(this);
						print(")");
					}

					if (hasDefault) {
						print(sep);
						sep = "";
						print("(");
						for (SwitchCase x : allCases) {
							if (!x.isDefault()) {
								print(sep);
								sep = " && ";
								print("(v != ");
								x.getExpression().accept(this);
								print(")");
							}
						}
						print("))");
					}

					println(") {");
					indent++;
					indented = true;
				}

				wasCase = false;
				if (s instanceof BreakStatement || s instanceof ReturnStatement) {
					cases.clear();
				}
				s.accept(this);
			}

		}

		if (indented) {
			indent--;
			printlni("}");
		}

		String label = enumSwitchLabel();
		println(label + ":;");
		enumSwitches.remove(enumSwitches.size() - 1);
		enumSwitchCount++;
		indent--;
		printlni("}");
	}

	private String enumSwitchLabel() {
		return "end_switch" + enumSwitchCount;
	}

	private void nativeSwitch(SwitchStatement node, List<Statement> statements) {
		printi("switch (");
		node.getExpression().accept(this);
		println(") {");

		boolean indented = false;
		boolean wasCase = false;
		for (int i = 0; i < statements.size(); ++i) {
			Statement s = statements.get(i);

			if (s instanceof VariableDeclarationStatement) {
				if (wasCase) {
					println();
				}
				VariableDeclarationStatement vds = (VariableDeclarationStatement) s;

				for (VariableDeclarationFragment fragment : (Iterable<VariableDeclarationFragment>) vds
						.fragments()) {
					if (fragment.getInitializer() != null) {
						printi();
						fragment.getName().accept(this);
						print(" = ");
						fragment.getInitializer().accept(this);
						println(";");
					}
				}
			} else if (s instanceof SwitchCase) {
				if (wasCase) {
					println();
				}

				if (indented) {
					indent--;
				}

				s.accept(this);

				if (i == statements.size() - 1) {
					println(" { }");
				}
				indent++;
				indented = true;
			} else if (s instanceof Block) {
				if (wasCase) {
					print(" ");
				}
				s.accept(this);
				println();
			} else {
				if (wasCase) {
					println();
				}

				s.accept(this);
			}

			wasCase = s instanceof SwitchCase;
		}

		if (indented) {
			indent--;
			indented = false;
		}

		printlni("}");
	}

	private List<VariableDeclarationStatement> declarations(
			List<Statement> statements) {
		List<VariableDeclarationStatement> ret = new ArrayList<VariableDeclarationStatement>();
		for (Statement s : statements) {
			if (s instanceof VariableDeclarationStatement) {
				ret.add((VariableDeclarationStatement) s);
			}
		}
		return ret;
	}

	private int sc;

	@Override
	public boolean visit(SynchronizedStatement node) {
		printlni("{");
		indent++;
		printi("synchronized synchronized_" + sc + "(");
		hardDep(node.getExpression().resolveTypeBinding());
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
		if (!qualify(node.getQualifier())) {
			print("this");
		}

		return false;
	}

	private boolean qualify(Name qualifier) {
		if (qualifier == null) {
			return false;
		}

		ITypeBinding qt = qualifier.resolveTypeBinding();
		if (type.isSubTypeCompatible(qt)) {
			return false;
		}

		String sep = "";
		for (ITypeBinding x = type; x.getDeclaringClass() != null
				&& !x.isSubTypeCompatible(qt); x = x.getDeclaringClass()) {
			hardDep(x.getDeclaringClass());

			print(sep + TransformUtil.outerThisName(x));
			sep = "->";
		}

		return true;
	}

	@Override
	public boolean visit(ThrowStatement node) {
		printi("throw ");
		node.getExpression().accept(this);
		hardDep(node.getExpression().resolveTypeBinding());
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

			printi("auto finally" + fc + " = finally([&] ");
			node.getFinally().accept(this);
			println(");");
			fc++;
		}

		if (!node.catchClauses().isEmpty()) {
			printi("try ");
		} else {
			printi();
		}

		List<VariableDeclarationExpression> resources = node.resources();
		if (!node.resources().isEmpty()) {
			print("(");
			for (Iterator<VariableDeclarationExpression> it = resources
					.iterator(); it.hasNext();) {
				VariableDeclarationExpression variable = it.next();
				variable.accept(this);
				if (it.hasNext()) {
					print(";");
				}
			}
			print(")");
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
		ImplWriter iw = new ImplWriter(root, ctx, tb, unitInfo);
		try {
			iw.write(node);
		} catch (Exception e) {
			throw new Error(e);
		}

		if (tb.isLocal()) {
			localTypes.put(tb, iw);
		}

		return false;
	}

	@Override
	public boolean visit(TypeLiteral node) {
		if (node.getType().isPrimitiveType()) {
			Code code = ((PrimitiveType) node.getType()).getPrimitiveTypeCode();
			if (code.equals(PrimitiveType.BOOLEAN)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Boolean"));
				print("::java::lang::Boolean::TYPE()");
			} else if (code.equals(PrimitiveType.BYTE)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Byte"));
				print("::java::lang::Byte::TYPE()");
			} else if (code.equals(PrimitiveType.CHAR)) {
				hardDep(node.getAST().resolveWellKnownType(
						"java.lang.Character"));
				print("::java::lang::Character::TYPE()");
			} else if (code.equals(PrimitiveType.DOUBLE)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Double"));
				print("::java::lang::Double::TYPE()");
			} else if (code.equals(PrimitiveType.FLOAT)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Float"));
				print("::java::lang::Float::TYPE()");
			} else if (code.equals(PrimitiveType.INT)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Integer"));
				print("::java::lang::Integer::TYPE()");
			} else if (code.equals(PrimitiveType.LONG)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Long"));
				print("::java::lang::Long::TYPE()");
			} else if (code.equals(PrimitiveType.SHORT)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Short"));
				print("::java::lang::Short::TYPE()");
			} else if (code.equals(PrimitiveType.VOID)) {
				hardDep(node.getAST().resolveWellKnownType("java.lang.Void"));
				print("::java::lang::Void::TYPE()");
			}
		} else {
			hardDep(node.getType().resolveBinding());
			node.getType().accept(this);
			print("::class_()");
		}
		return false;
	}

	@Override
	public boolean visit(TypeParameter node) {
		node.getName().accept(this);
		if (!node.typeBounds().isEmpty()) {
			print(" extends ");
			for (Iterator<Type> it = node.typeBounds().iterator(); it.hasNext();) {
				Type t = it.next();
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
		for (Iterator<Type> it = node.types().iterator(); it.hasNext();) {
			Type t = it.next();
			t.accept(this);
			if (it.hasNext()) {
				print("|");
			}
		}
		return false;
	}

	@Override
	public boolean visit(VariableDeclarationFragment node) {
		IVariableBinding vb = node.resolveBinding();
		if (!vb.isField()) {
			locals.get(locals.size() - 1).add(CName.of(vb));
		}

		print(TransformUtil.ref(vb.getType()));

		if (node.getInitializer() != null) {
			print(TransformUtil.constVar(vb));
			node.getName().accept(this);
			hardDep(node.getInitializer().resolveTypeBinding());
			print(" = ");
			node.getInitializer().accept(this);
		} else {
			node.getName().accept(this);
		}

		return false;
	}

	@Override
	public boolean visit(WhileStatement node) {
		printi("while (");
		node.getExpression().accept(this);
		print(") ");
		handleLoopBody(node, node.getBody());

		return false;
	}
}
