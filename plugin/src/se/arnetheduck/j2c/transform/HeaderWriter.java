package se.arnetheduck.j2c.transform;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

public class HeaderWriter extends TransformWriter {
	private final IPath root;

	private String lastAccess;

	private boolean hasClinit;
	private boolean hasInit;

	private final List<MethodDeclaration> constructors = new ArrayList<MethodDeclaration>();
	private final List<IMethodBinding> methods = new ArrayList<IMethodBinding>();

	private Set<String> usings = new HashSet<String>();

	public HeaderWriter(IPath root, Transformer ctx, ITypeBinding type,
			UnitInfo unitInfo) {
		super(ctx, type, unitInfo);
		this.root = root;
	}

	public void write(TypeDeclaration node) throws Exception {
		writeType(node.getAST(), new ArrayList<EnumConstantDeclaration>(),
				node.bodyDeclarations(), new ArrayList<IVariableBinding>());
	}

	public void write(AnnotationTypeDeclaration node) throws Exception {
		writeType(node.getAST(), new ArrayList<EnumConstantDeclaration>(),
				node.bodyDeclarations(), new ArrayList<IVariableBinding>());
	}

	public void write(EnumDeclaration node) throws Exception {
		writeType(node.getAST(), node.enumConstants(), node.bodyDeclarations(),
				new ArrayList<IVariableBinding>());
	}

	public void writeType(AST ast, List<EnumConstantDeclaration> enums,
			List<BodyDeclaration> declarations,
			Collection<IVariableBinding> closures) {

		ctx.headers.add(type);
		try {
			out = TransformUtil.openHeader(root, type);

			String body = getBody(ast, enums, declarations, closures);
			for (ITypeBinding tb : hardDeps) {
				out.println(TransformUtil.include(tb));
			}

			out.print(body);

			out.close();
		} catch (IOException e) {
			throw new Error(e);
		}
	}

	private String getBody(AST ast, List<EnumConstantDeclaration> enums,
			List<BodyDeclaration> declarations,
			Collection<IVariableBinding> closures) {

		PrintWriter old = out;
		StringWriter sw = new StringWriter();
		out = new PrintWriter(sw);

		List<ITypeBinding> bases = TransformUtil.getBases(ast, type);

		for (ITypeBinding base : bases) {
			hardDep(base);
		}

		println();

		if (type.isInterface()) {
			lastAccess = TransformUtil.PUBLIC;
			print("struct ");
		} else {
			lastAccess = TransformUtil.PRIVATE;
			print("class ");
		}

		println(TransformUtil.qualifiedCName(type, false));

		String sep = ": public ";

		indent++;
		for (ITypeBinding base : bases) {
			printlni(sep, TransformUtil.virtual(base),
					TransformUtil.relativeCName(base, type, true));
			sep = ", public ";
		}
		indent--;

		println("{");

		indent++;

		if (!type.isInterface()) {
			printi("typedef ");
			if (type.getSuperclass() != null) {
				print(TransformUtil.relativeCName(type.getSuperclass(), type,
						true));
			} else {
				print("::java::lang::Object");
			}

			println(" super;");
		}

		for (ITypeBinding nb : unitInfo.types) {
			if (!nb.isEqualTo(type)) {
				printlni("friend class ", TransformUtil.name(nb), ";");
			}
		}

		lastAccess = TransformUtil
				.printAccess(out, Modifier.PUBLIC, lastAccess);

		visitAll(enums);

		printlni("static ::java::lang::Class *class_;");

		if (type.getQualifiedName().equals("java.lang.Object")) {
			out.println("public:");
			out.print(TransformUtil.indent(1));
			out.println("virtual ~Object();");
		}

		visitAll(declarations); // This will gather constructors

		lastAccess = TransformUtil
				.printAccess(out, Modifier.PUBLIC, lastAccess);

		if (!type.isInterface()) {
			if (type.isAnonymous()) {
				makeBaseConstructors(closures);
			} else {
				makeConstructors(closures);
			}

			ITypeBinding sb = type.getSuperclass();
			boolean superInner = sb != null && TransformUtil.hasOuterThis(sb);
			if (TransformUtil.hasOuterThis(type)) {
				if (!superInner
						|| sb.getDeclaringClass() != null
						&& !type.getDeclaringClass().getErasure()
								.isEqualTo(sb.getDeclaringClass().getErasure())) {
					printlni(TransformUtil.outerThis(type), ";");
				}
			}

			if (closures != null) {
				for (IVariableBinding closure : closures) {
					printlni(TransformUtil.relativeCName(closure.getType(),
							type, false), " ", TransformUtil.ref(closure
							.getType()), closure.getName(), "_;");
				}
			}

			makeBaseCalls();

			makeEnumMethods();

			makeClinit();
			makeInit();
		}

		indent--;

		println("};");

		TransformUtil.printStringSupport(type, out);

		out.close();

		out = old;
		return sw.toString();
	}

	private void makeClinit() {
		if (!hasClinit) {
			return;
		}

		lastAccess = TransformUtil.printAccess(out, Modifier.PRIVATE,
				lastAccess);

		printlni("static struct ", TransformUtil.STATIC_INIT, " { ",
				TransformUtil.STATIC_INIT, "(); } ", TransformUtil.STATIC_INIT,
				";");
	}

	public void makeInit() {
		if (!hasInit) {
			return;
		}

		lastAccess = TransformUtil.printAccess(out, Modifier.PRIVATE,
				lastAccess);

		printlni("void ", TransformUtil.INSTANCE_INIT, "();");
	}

	private void makeConstructors(Collection<IVariableBinding> closures) {
		String name = TransformUtil.name(type);

		boolean hasEmpty = false;
		for (MethodDeclaration md : constructors) {
			printi(name, "(");

			String sep = printNestedParams(closures);

			if (!md.parameters().isEmpty()) {
				print(sep);
				visitAllCSV(md.parameters(), false);
			}

			hasEmpty |= md.parameters().isEmpty();
			println(")", TransformUtil.throwsDecl(md.thrownExceptions()), ";");
		}

		if (!hasEmpty) {
			if (!constructors.isEmpty() && !"protected:".equals(lastAccess)) {
				lastAccess = "protected:";
				println(lastAccess);
			}

			printi(name, "(");

			String sep = printNestedParams(closures);

			println(");");

			printlni("void ", TransformUtil.CTOR, "() { }");
		}
	}

	private void makeBaseConstructors(Collection<IVariableBinding> closures) {
		// Synthesize base class constructors
		String name = TransformUtil.name(type);
		boolean fake = true;
		for (IMethodBinding mb : type.getSuperclass().getDeclaredMethods()) {
			if (!mb.isConstructor()) {
				continue;
			}

			fake = false;
			printi(name, "(");

			String sep = printNestedParams(closures);

			for (int i = 0; i < mb.getParameterTypes().length; ++i) {
				ITypeBinding pb = mb.getParameterTypes()[i];
				ctx.softDep(pb);

				print(sep, TransformUtil.relativeCName(pb, type, true), " ",
						TransformUtil.ref(pb), "a" + i);
				sep = ", ";
			}

			println(");");
		}

		if (fake) {
			printi(name, "(");
			printNestedParams(closures);
			println(");");
		}
	}

	// In java, if a super class implements the method of an interface, it
	// doesn't
	// have to be reimplemented on the class implementing the interface
	private void makeBaseCalls() {
		if (Modifier.isAbstract(type.getModifiers())) {
			return;
		}

		Set<IMethodBinding> im = new TreeSet<IMethodBinding>(
				new Transformer.BindingComparator());

		for (ITypeBinding ib : interfaces(type)) {
			im.addAll(Arrays.asList(ib.getDeclaredMethods()));
		}

		List<IMethodBinding> missing = new ArrayList<IMethodBinding>(im);

		for (IMethodBinding imb : im) {
			for (IMethodBinding mb : methods) {
				if (mb.isSubsignature(imb)) {
					missing.remove(imb);
					break;
				}
			}

			// Same method in two interfaces
			for (IMethodBinding mb : missing) {
				if (!mb.isEqualTo(imb) && mb.isSubsignature(imb)) {
					missing.remove(imb);
					break;
				}
			}
		}

		for (IMethodBinding mb : missing) {
			lastAccess = TransformUtil.printAccess(out, Modifier.PUBLIC,
					lastAccess);

			// These should be declared on a base type if the java code is valid
			printi();
			TransformUtil.printSignature(out, type, mb, ctx, false);
			print(" { return super::", TransformUtil.name(mb), "(");
			String sep = "";
			for (int i = 0; i < mb.getParameterTypes().length; ++i) {
				print(sep, "a", i);
				sep = ", ";
			}
			println("); }");
		}
	}

	/** Generate implicit enum methods */
	private void makeEnumMethods() {
		if (!type.isEnum()) {
			return;
		}

		lastAccess = TransformUtil
				.printAccess(out, Modifier.PUBLIC, lastAccess);

		for (IMethodBinding mb : type.getDeclaredMethods()) {
			if (type.createArrayType(1).isEqualTo(mb.getReturnType())
					&& mb.getName().equals("values")
					&& mb.getParameterTypes().length == 0) {
				printi();
				TransformUtil.printSignature(out, type, mb, ctx, false);
				println(" { return nullptr; /* TODO */ }");
			} else if (type.isEqualTo(mb.getReturnType())
					&& mb.getName().equals("valueOf")
					&& mb.getParameterTypes().length == 1
					&& mb.getParameterTypes()[0].getQualifiedName().equals(
							String.class.getName())) {
				printi();
				TransformUtil.printSignature(out, type, mb, ctx, false);
				println(" { return nullptr; /* TODO */ }");
			}
		}
	}

	private static List<ITypeBinding> interfaces(ITypeBinding tb) {
		if (tb.getInterfaces().length == 0) {
			return Collections.emptyList();
		}

		List<ITypeBinding> ret = new ArrayList<ITypeBinding>();
		for (ITypeBinding ib : tb.getInterfaces()) {
			ret.add(ib);
			ret.addAll(interfaces(ib));
		}

		return ret;
	}

	@Override
	public boolean preVisit2(ASTNode node) {
		for (Snippet snippet : ctx.snippets) {
			if (!snippet.node(ctx, this, node)) {
				return false;
			}
		}

		return super.preVisit2(node);
	}

	private List<Class<?>> handledBlocks = new ArrayList<Class<?>>(
			Arrays.asList(TypeDeclaration.class));

	@Override
	public boolean visit(AnnotationTypeMemberDeclaration node) {
		printi();
		TransformUtil.printSignature(out, type, node.resolveBinding(), ctx,
				false);
		// TODO defaults
		println(" = 0;");
		return false;
	}

	@Override
	public boolean visit(Block node) {
		if (!handledBlocks.contains(node.getParent().getClass())) {
			printlni("{");

			indent++;

			for (Object o : node.statements()) {
				Statement s = (Statement) o;
				s.accept(this);
			}

			indent--;
			printlni("}");
			println();
		}

		return false;
	}

	@Override
	public boolean visit(EnumDeclaration node) {
		return false;
	}

	@Override
	public boolean visit(EnumConstantDeclaration node) {
		printi("static ", TransformUtil.name(type), " *");

		node.getName().accept(this);
		println(";");

		return false;
	}

	@Override
	public boolean visit(FieldDeclaration node) {
		if (node.getJavadoc() != null) {
			node.getJavadoc().accept(this);
		}

		lastAccess = TransformUtil.printAccess(out, node.getModifiers(),
				lastAccess);

		List<VariableDeclarationFragment> fragments = node.fragments();

		ITypeBinding tb = node.getType().resolveBinding();
		if (isAnyArray(fragments)) {
			for (VariableDeclarationFragment f : fragments) {
				printi(TransformUtil.fieldModifiers(type, node.getModifiers(),
						true, hasInitilializer(fragments)));

				ITypeBinding at = f.getExtraDimensions() > 0 ? tb
						.createArrayType(f.getExtraDimensions()) : tb;

				print(TransformUtil.relativeCName(at, type, true), " ");

				f.accept(this);

				println(";");
			}
		} else {
			printi(TransformUtil.fieldModifiers(type, node.getModifiers(),
					true, hasInitilializer(fragments)));

			print(TransformUtil.relativeCName(tb, type, true), " ");

			print(" ");

			visitAllCSV(fragments, false);

			println(";");
		}

		return false;
	}

	private static boolean isAnyArray(
			List<VariableDeclarationFragment> fragments) {
		for (VariableDeclarationFragment f : fragments) {
			if (f.getExtraDimensions() != 0) {
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean visit(Initializer node) {
		if (Modifier.isStatic(node.getModifiers())) {
			hasClinit = true;
		} else {
			hasInit = true;
		}

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
	public boolean visit(MethodDeclaration node) {
		if (node.getJavadoc() != null) {
			node.getJavadoc().accept(this);
		}

		IMethodBinding mb = node.resolveBinding();

		if ((Modifier.isAbstract(mb.getModifiers()) || type.isInterface())
				&& TransformUtil.baseHasSame(mb, type, ctx)) {
			// Defining once more will lead to virtual inheritance issues
			printi("/*");
			TransformUtil.printSignature(out, type, mb, ctx, false);
			println("; (already declared) */");
			return false;
		}

		if (node.isConstructor()) {
			constructors.add(node);

			if (!"protected:".equals(lastAccess)) {
				lastAccess = "protected:";
				println(lastAccess);
			}

			printi("void ", TransformUtil.CTOR);
		} else {
			methods.add(mb);
			lastAccess = TransformUtil.printAccess(out, node.getModifiers(),
					lastAccess);

			printi(TransformUtil.methodModifiers(node.getModifiers(),
					type.getModifiers()));
			print(TransformUtil.typeParameters(node.typeParameters()));

			ITypeBinding rt = TransformUtil.returnType(node);
			ctx.softDep(rt);
			print(TransformUtil.qualifiedCName(rt, true), " ",
					TransformUtil.ref(rt));

			node.getName().accept(this);

			IMethodBinding mb2 = TransformUtil.getSuperMethod(mb);

			if (mb2 != null && TransformUtil.returnCovariant(mb, mb2)) {
				hardDep(mb.getReturnType());
			}
		}

		visitAllCSV(node.parameters(), true);

		print(TransformUtil.throwsDecl(node.thrownExceptions()));

		if (node.getBody() == null && !Modifier.isNative(node.getModifiers())) {
			print(" = 0");
		}

		println(";");

		if (!node.isConstructor()) {
			String using = TransformUtil.methodUsing(mb);
			if (using != null) {
				if (usings.add(using)) {
					printlni(using);
				}
			}
		}

		TransformUtil.declareBridge(out, type, mb, ctx);
		return false;
	}

	@Override
	public boolean visit(SimpleName node) {
		IBinding b = node.resolveBinding();
		if (b instanceof ITypeBinding) {
			ctx.softDep((ITypeBinding) b);
			print(TransformUtil.relativeCName((ITypeBinding) b, type, false));
			return false;
		}
		return super.visit(node);
	}

	@Override
	public boolean visit(SimpleType node) {
		ITypeBinding b = node.resolveBinding();
		ctx.softDep(b);
		print(TransformUtil.relativeCName(b, type, false));
		return false;
	}

	@Override
	public boolean visit(QualifiedName node) {
		IBinding b = node.resolveBinding();
		if (b instanceof ITypeBinding) {
			ctx.softDep((ITypeBinding) b);
			print(TransformUtil.relativeCName((ITypeBinding) b, type, false));
			return false;
		}
		return super.visit(node);
	}

	@Override
	public boolean visit(QualifiedType node) {
		ITypeBinding b = node.resolveBinding();
		ctx.softDep(b);
		print(TransformUtil.relativeCName(b, type, false));
		return false;
	}

	@Override
	public boolean visit(SingleVariableDeclaration node) {
		ITypeBinding tb = node.getType().resolveBinding();

		if (node.getExtraDimensions() > 0) {
			ctx.softDep(tb);
			tb = tb.createArrayType(node.getExtraDimensions());
		}

		if (node.isVarargs()) {
			tb = tb.createArrayType(1);
			print(TransformUtil.relativeCName(tb, type, true));
			print("/*...*/");
		} else {
			print(TransformUtil.relativeCName(tb, type, true));
		}

		print(" ", TransformUtil.ref(tb));

		node.getName().accept(this);

		return false;
	}

	@Override
	public boolean visit(TypeDeclaration node) {
		return false;
	}

	@Override
	public boolean visit(VariableDeclarationFragment node) {
		ITypeBinding tb = node.resolveBinding().getType();
		ctx.softDep(tb);
		print(TransformUtil.ref(tb));

		node.getName().accept(this);
		Object v = TransformUtil.constantValue(node);
		if (v != null) {
			print(" = ", v);
		} else {
			if (!Modifier.isStatic(node.resolveBinding().getModifiers())
					&& node.getInitializer() != null) {
				hasInit = true;
			}
		}

		return false;
	}
}
