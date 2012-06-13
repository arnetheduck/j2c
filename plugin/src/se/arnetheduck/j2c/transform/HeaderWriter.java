package se.arnetheduck.j2c.transform;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
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
	private final Collection<IVariableBinding> closures;

	private final Set<String> usings = new HashSet<String>();
	private final List<MethodDeclaration> constructors = new ArrayList<MethodDeclaration>();
	private final List<IMethodBinding> methods = new ArrayList<IMethodBinding>();

	private String lastAccess;

	private boolean hasInit;

	public HeaderWriter(IPath root, Transformer ctx, ITypeBinding type,
			UnitInfo unitInfo, Collection<IVariableBinding> closures) {
		super(ctx, type, unitInfo);
		this.root = root;
		this.closures = closures;

		lastAccess = HeaderUtil.initialAccess(type);
	}

	public void write(AnnotationTypeDeclaration node) throws Exception {
		writeType(new ArrayList<EnumConstantDeclaration>(),
				node.bodyDeclarations());
	}

	public void write(AnonymousClassDeclaration node) throws Exception {
		writeType(new ArrayList<EnumConstantDeclaration>(),
				node.bodyDeclarations());
	}

	public void write(EnumDeclaration node) throws Exception {
		writeType(node.enumConstants(), node.bodyDeclarations());
	}

	public void write(TypeDeclaration node) throws Exception {
		writeType(new ArrayList<EnumConstantDeclaration>(),
				node.bodyDeclarations());
	}

	private void writeType(List<EnumConstantDeclaration> enums,
			List<BodyDeclaration> declarations) {

		try {
			String body = getBody(enums, declarations);

			PrintWriter pw = HeaderUtil.open(root, type, ctx, softDeps,
					hardDeps);

			pw.print(body);

			pw.close();
		} catch (Exception e) {
			throw new Error(e);
		}

		for (ITypeBinding tb : softDeps) {
			ctx.softDep(tb);
		}
	}

	private String getBody(List<EnumConstantDeclaration> enums,
			List<BodyDeclaration> declarations) {
		PrintWriter old = out;
		StringWriter sw = new StringWriter();
		out = new PrintWriter(sw);

		out.println("{");

		indent++;

		lastAccess = HeaderUtil.printSuper(out, type, lastAccess);
		lastAccess = HeaderUtil.printClassLiteral(out, lastAccess);

		visitAll(enums);

		visitAll(declarations); // This will gather constructors

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
					softDep(type.getDeclaringClass());
					printlni(TransformUtil.outerThis(type), ";");
				}
			}

			if (closures != null) {
				for (IVariableBinding closure : closures) {
					softDep(closure.getType());
					printlni(TransformUtil.relativeCName(closure.getType(),
							type, true), " ", TransformUtil.ref(closure
							.getType()), closure.getName(), "_;");
				}
			}

			makeBaseCalls();

			lastAccess = HeaderUtil.printEnumMethods(out, type, softDeps,
					lastAccess);

			makeInit();

			lastAccess = HeaderUtil.printClinit(out, lastAccess);
			lastAccess = HeaderUtil.printDtor(out, type, lastAccess);
			lastAccess = HeaderUtil.printGetClass(out, type, lastAccess);

			HeaderUtil.printStringOperator(out, type);

			for (ITypeBinding nb : unitInfo.types) {
				softDep(nb);
				if (!nb.isEqualTo(type)) {
					printlni("friend class ", TransformUtil.name(nb), ";");
				}
			}
		}

		indent--;

		println("};");

		TransformUtil.printStringSupport(type, out);

		out.close();

		out = old;
		return sw.toString();
	}

	public void makeInit() {
		if (!hasInit) {
			return;
		}

		lastAccess = HeaderUtil.printAccess(out, Modifier.PRIVATE, lastAccess);

		printlni("void ", TransformUtil.INSTANCE_INIT, "();");
	}

	private void makeConstructors(Collection<IVariableBinding> closures) {
		String name = TransformUtil.name(type);

		boolean hasEmpty = false;
		for (MethodDeclaration md : constructors) {
			if (md.parameters().size() == 0
					&& Modifier.isPrivate(md.getModifiers())) {
				if (!"protected:".equals(lastAccess)) {
					lastAccess = "protected:";
					println(lastAccess);
				}
			} else {
				lastAccess = HeaderUtil.printAccess(out, md.getModifiers(),
						lastAccess);
			}

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
			if (constructors.size() > 0 && !"protected:".equals(lastAccess)) {
				lastAccess = "protected:";
				println(lastAccess);
			} else {
				lastAccess = HeaderUtil.printAccess(out, Modifier.PUBLIC,
						lastAccess);
			}

			printi(name, "(");

			printNestedParams(closures);

			println(");");

			printlni("void ", TransformUtil.CTOR, "();");
		}
	}

	private void makeBaseConstructors(Collection<IVariableBinding> closures) {
		lastAccess = HeaderUtil.printAccess(out, Modifier.PUBLIC, lastAccess);
		// Synthesize base class constructors
		String name = TransformUtil.name(type);
		boolean fake = true;
		for (IMethodBinding mb : type.getSuperclass().getDeclaredMethods()) {
			if (!mb.isConstructor() || Modifier.isPrivate(mb.getModifiers())) {
				continue;
			}

			fake = false;
			printi(name, "(");

			String sep = printNestedParams(closures);

			for (int i = 0; i < mb.getParameterTypes().length; ++i) {
				ITypeBinding pb = mb.getParameterTypes()[i];
				softDep(pb);

				print(sep, TransformUtil.relativeCName(pb, type, true), " ",
						TransformUtil.ref(pb), TransformUtil.paramName(mb, i));
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

	private void makeBaseCalls() {
		if (Modifier.isAbstract(type.getModifiers())) {
			return;
		}

		List<IMethodBinding> missing = HeaderUtil.baseCallMethods(type);

		for (IMethodBinding mb : missing) {
			lastAccess = HeaderUtil.printAccess(out, Modifier.PUBLIC,
					lastAccess);

			printi();
			TransformUtil.printSuperCall(out, type, mb, softDeps);

			String using = TransformUtil.methodUsing(mb, type);
			if (using != null) {
				if (usings.add(using)) {
					printlni(using);
				}
			}
		}
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
		TransformUtil.printSignature(out, type, node.resolveBinding(),
				softDeps, false);
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

		lastAccess = HeaderUtil.printAccess(out, node.getModifiers(),
				lastAccess);

		List<VariableDeclarationFragment> fragments = node.fragments();

		ITypeBinding tb = node.getType().resolveBinding();
		if (isAnySpecial(fragments)) {
			for (VariableDeclarationFragment f : fragments) {
				IVariableBinding vb = f.resolveBinding();
				boolean asMethod = TransformUtil.asMethod(vb);
				if (asMethod) {
					lastAccess = HeaderUtil.printAccess(out,
							node.getModifiers(), lastAccess);
					printi("static ", TransformUtil.relativeCName(vb.getType(),
							type, true), " ");

					print(TransformUtil.ref(vb.getType()), "&",
							TransformUtil.name(vb));
					println("();");

					lastAccess = HeaderUtil.printAccess(out, Modifier.PRIVATE,
							lastAccess);
				}

				printi(TransformUtil.fieldModifiers(type, node.getModifiers(),
						true, TransformUtil.constantValue(f) != null));

				print(TransformUtil.relativeCName(vb.getType(), type, true),
						" ");

				f.accept(this);

				println(asMethod ? "_;" : ";");
			}
		} else {
			printi(TransformUtil.fieldModifiers(type, node.getModifiers(),
					true, hasInitilializer(fragments)));

			print(TransformUtil.relativeCName(tb, type, true), " ");

			visitAllCSV(fragments, false);

			println(";");
		}

		return false;
	}

	/**
	 * Fields that for some reason cannot be declared together (different C++
	 * type, implemented as methods, etc)
	 */
	private static boolean isAnySpecial(
			List<VariableDeclarationFragment> fragments) {
		for (VariableDeclarationFragment f : fragments) {
			if (f.getExtraDimensions() != 0) {
				return true;
			}

			if (TransformUtil.constantValue(f) != null) {
				return true;
			}

			if (TransformUtil.isStatic(f.resolveBinding())) {
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean visit(Initializer node) {
		if (!Modifier.isStatic(node.getModifiers())) {
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
			TransformUtil.printSignature(out, type, mb, softDeps, false);
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
			lastAccess = HeaderUtil.printAccess(out, node.getModifiers(),
					lastAccess);

			printi(TransformUtil.methodModifiers(node.getModifiers(),
					type.getModifiers()));
			print(TransformUtil.typeParameters(node.typeParameters()));

			ITypeBinding rt = TransformUtil.returnType(node);
			softDep(rt);
			print(TransformUtil.relativeCName(rt, type, true), " ",
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

		TransformUtil.declareBridge(out, type, mb, softDeps);
		return false;
	}

	@Override
	public boolean visit(SimpleName node) {
		IBinding b = node.resolveBinding();
		if (b instanceof ITypeBinding) {
			softDep((ITypeBinding) b);
			print(TransformUtil.relativeCName((ITypeBinding) b, type, false));
			return false;
		}
		return super.visit(node);
	}

	@Override
	public boolean visit(SimpleType node) {
		ITypeBinding b = node.resolveBinding();
		softDep(b);
		print(TransformUtil.relativeCName(b, type, false));
		return false;
	}

	@Override
	public boolean visit(QualifiedName node) {
		IBinding b = node.resolveBinding();
		if (b instanceof ITypeBinding) {
			softDep((ITypeBinding) b);
			print(TransformUtil.relativeCName((ITypeBinding) b, type, false));
			return false;
		}
		return super.visit(node);
	}

	@Override
	public boolean visit(QualifiedType node) {
		ITypeBinding b = node.resolveBinding();
		softDep(b);
		print(TransformUtil.relativeCName(b, type, false));
		return false;
	}

	@Override
	public boolean visit(SingleVariableDeclaration node) {
		ITypeBinding tb = node.getType().resolveBinding();

		if (node.getExtraDimensions() > 0) {
			softDep(tb);
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
		softDep(tb);
		print(TransformUtil.ref(tb));

		node.getName().accept(this);
		Object v = TransformUtil.constantValue(node);
		if (v != null) {
			print(" = ", v);
		} else {
			if (node.getInitializer() != null) {
				if (!Modifier.isStatic(node.resolveBinding().getModifiers())) {
					hasInit = true;
				}
			}
		}

		return false;
	}
}
