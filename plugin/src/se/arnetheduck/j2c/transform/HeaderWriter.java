package se.arnetheduck.j2c.transform;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
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

	private boolean hasInitializer;
	private String initializer;

	private List<MethodDeclaration> constructors = new ArrayList<MethodDeclaration>();

	private Set<String> usings = new HashSet<String>();

	public HeaderWriter(IPath root, Transformer ctx, ITypeBinding type) {
		super(ctx, type);
		this.root = root;
	}

	public void write(TypeDeclaration node) throws Exception {
		writeType(node.getAST(), node.bodyDeclarations(),
				new ArrayList<IVariableBinding>());
	}

	public void writeType(AST ast, List<BodyDeclaration> declarations,
			Collection<IVariableBinding> closures) {
		try {
			out = TransformUtil.openHeader(root, type);
			List<ITypeBinding> bases = TransformUtil.getBases(ast, type);

			for (ITypeBinding base : bases) {
				hardDep(base);
				println(TransformUtil.include(base));
			}

			println();

			print("class ", TransformUtil.qualifiedCName(type));

			String sep = " : public ";

			for (ITypeBinding base : bases) {
				print(sep, TransformUtil.virtual(base),
						TransformUtil.relativeCName(base, type));
				sep = ", public ";
			}

			println();
			println("{");

			indent++;

			if (!type.isInterface()) {
				printi("typedef ");
				if (type.getSuperclass() != null) {
					print(TransformUtil.relativeCName(type.getSuperclass(),
							type));
				} else {
					print("java::lang::Object");
				}

				println(" super;");
			}

			visitAll(declarations); // This will gather constructors

			lastAccess = TransformUtil.printAccess(out, Modifier.PUBLIC,
					lastAccess);

			if (type.isAnonymous()) {
				makeBaseConstructors(closures);
			} else {
				makeConstructors(closures);
			}

			if (TransformUtil.isInner(type) && !TransformUtil.outerStatic(type)) {
				printlni(TransformUtil.outerThis(type), ";");
			}

			if (closures != null) {
				for (IVariableBinding closure : closures) {
					printlni(TransformUtil.relativeCName(closure.getType(),
							type), " ", TransformUtil.ref(closure.getType()),
							closure.getName(), "_;");
				}
			}

			indent--;

			println("};");

			out.close();

			ctx.headers.add(type);
		} catch (IOException e) {
			throw new Error(e);
		}
	}

	private void makeConstructors(Collection<IVariableBinding> closures) {
		String name = TransformUtil.name(type);

		if (constructors.isEmpty() && TransformUtil.isInner(type)
				|| (closures != null && !closures.isEmpty())) {
			printi(name, "(");
			printNestedParams(closures);
			println(");");
		}

		for (MethodDeclaration md : constructors) {
			printi(name, "(");

			String sep = printNestedParams(closures);

			if (!md.parameters().isEmpty()) {
				print(sep);
				visitAllCSV(md.parameters(), false);
			}

			println(") ", TransformUtil.throwsDecl(md.thrownExceptions()), ";");
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

				print(sep, TransformUtil.relativeCName(pb, type), " ",
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

	private List<Class<?>> handledBlocks = new ArrayList<Class<?>>(
			Arrays.asList(TypeDeclaration.class));

	@Override
	public boolean visit(Block node) {
		if (handledBlocks.contains(node.getParent().getClass())) {
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
	public boolean visit(EnumConstantDeclaration node) {
		if (node.getJavadoc() != null) {
			node.getJavadoc().accept(this);
		}

		printi();

		node.getName().accept(this);

		if (!node.arguments().isEmpty()) {
			visitAllCSV(node.arguments(), true);
		}

		if (node.getAnonymousClassDeclaration() != null) {
			node.getAnonymousClassDeclaration().accept(this);
		}
		return false;
	}

	@Override
	public boolean visit(EnumDeclaration node) {
		if (node.getJavadoc() != null) {
			node.getJavadoc().accept(this);
		}

		// printModifiers(node.modifiers());
		printi("enum ");
		node.getName().accept(this);

		if (!node.superInterfaceTypes().isEmpty()) {
			print(" implements ");

			visitAllCSV(node.superInterfaceTypes(), false);
		}

		print(" {");

		indent++;

		visitAllCSV(node.enumConstants(), false);

		if (!node.bodyDeclarations().isEmpty()) {
			print("; ");
			visitAll(node.bodyDeclarations());
		}

		indent--;

		printlni("}\n");
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

		if (isAnyArray(fragments)) {
			for (VariableDeclarationFragment f : fragments) {
				printi(TransformUtil.fieldModifiers(node.getModifiers(), true,
						hasInitilializer(fragments)));

				ITypeBinding at = node.getType().resolveBinding()
						.createArrayType(f.getExtraDimensions());

				print(TransformUtil.qualifiedCName(at), " ");

				f.accept(this);

				println(";");
			}
		} else {
			printi(TransformUtil.fieldModifiers(node.getModifiers(), true,
					hasInitilializer(fragments)));

			node.getType().accept(this);

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
		if (hasInitializer) {
			return false;
		}

		hasInitializer = true;

		lastAccess = TransformUtil.printAccess(out, Modifier.PRIVATE,
				lastAccess);
		println("private:");

		String name = TransformUtil.name(type);
		printlni("struct ", name, "Initializer {");

		indent++;

		printlni(name, "Initializer();");
		indent--;

		printlni("};");

		printlni("static ", name, "Initializer staticInitializer;");

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

		if (node.isConstructor()) {
			constructors.add(node);

			lastAccess = TransformUtil.printAccess(out, Modifier.PRIVATE,
					lastAccess);

			printi("void _construct");
		} else {
			lastAccess = TransformUtil.printAccess(out, node.getModifiers(),
					lastAccess);

			printi(TransformUtil.methodModifiers(node.getModifiers(),
					type.getModifiers()));
			print(TransformUtil.typeParameters(node.typeParameters()));

			node.getReturnType2().accept(this);
			print(" ", TransformUtil.ref(node.getReturnType2()));
			node.getName().accept(this);
		}

		visitAllCSV(node.parameters(), true);

		print(TransformUtil.throwsDecl(node.thrownExceptions()));

		if (node.getBody() == null && !Modifier.isNative(node.getModifiers())) {
			print(" = 0");
		}

		println(";");

		if (!node.isConstructor()) {
			IMethodBinding mb = node.resolveBinding();
			String using = TransformUtil.methodUsing(mb);
			if (using != null) {
				if (usings.add(using)) {
					printlni(using);
				}
			}
		}

		return false;
	}

	@Override
	public boolean visit(SimpleName node) {
		IBinding b = node.resolveBinding();
		if (b instanceof ITypeBinding) {
			ctx.softDep((ITypeBinding) b);
			print(TransformUtil.relativeCName((ITypeBinding) b, type));
			return false;
		}
		return super.visit(node);
	}

	@Override
	public boolean visit(SimpleType node) {
		ITypeBinding b = node.resolveBinding();
		ctx.softDep(b);
		print(TransformUtil.relativeCName(b, type));
		return false;
	}

	@Override
	public boolean visit(QualifiedName node) {
		IBinding b = node.resolveBinding();
		if (b instanceof ITypeBinding) {
			ctx.softDep((ITypeBinding) b);
			print(TransformUtil.relativeCName((ITypeBinding) b, type));
			return false;
		}
		return super.visit(node);
	}

	@Override
	public boolean visit(QualifiedType node) {
		ITypeBinding b = node.resolveBinding();
		ctx.softDep(b);
		print(TransformUtil.relativeCName(b, type));
		return false;
	}

	@Override
	public boolean visit(SingleVariableDeclaration node) {
		ITypeBinding tb = node.getType().resolveBinding();

		if (node.getExtraDimensions() > 0) {
			ctx.softDep(tb);
			tb = tb.createArrayType(node.getExtraDimensions());
			print(TransformUtil.relativeCName(tb, type));
		} else {
			node.getType().accept(this);
		}
		if (node.isVarargs()) {
			print("/*...*/");
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
		}

		return false;
	}
}
