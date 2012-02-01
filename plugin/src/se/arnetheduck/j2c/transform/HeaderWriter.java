package se.arnetheduck.j2c.transform;

import java.io.IOException;
import java.io.PrintWriter;
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
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

public class HeaderWriter extends TransformWriter {
	private static class State {
		public State(ITypeBinding tb, PrintWriter out) {
			this.tb = tb;
			this.out = out;
		}

		public ITypeBinding tb;

		public PrintWriter out;

		public boolean hasInitializer;

		public String lastAccess;

		public Set<String> usings = new HashSet<String>();
	}

	private final IPath root;

	private State state;

	public HeaderWriter(IPath root) {
		this.root = root;
	}

	private List<Class<?>> handledBlocks = new ArrayList<Class<?>>(
			Arrays.asList(TypeDeclaration.class));

	@Override
	public boolean visit(AnonymousClassDeclaration node) {
		/* todo */
		return false;
	}

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

		state.lastAccess = TransformUtil.printAccess(getOut(),
				node.getModifiers(), state.lastAccess);

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
		if (state.hasInitializer) {
			return false;
		}

		state.hasInitializer = true;

		state.lastAccess = TransformUtil.printAccess(getOut(),
				Modifier.PRIVATE, state.lastAccess);
		println("private:");

		String name = TransformUtil.name(state.tb);
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
		state.lastAccess = TransformUtil.printAccess(getOut(),
				node.getModifiers(), state.lastAccess);

		if (node.getJavadoc() != null) {
			node.getJavadoc().accept(this);
		}

		printi();

		if (!node.isConstructor()) {
			print(TransformUtil.methodModifiers(node.getModifiers(),
					state.tb.getModifiers()));
		}

		print(TransformUtil.typeParameters(node.typeParameters()));

		if (node.getReturnType2() != null) {
			node.getReturnType2().accept(this);
			print(" ", TransformUtil.ref(node.getReturnType2()));
		}

		node.getName().accept(this);

		visitAllCSV(node.parameters(), true);

		print(TransformUtil.throwsDecl(node.thrownExceptions()));

		if (node.getBody() == null && !Modifier.isNative(node.getModifiers())) {
			print(" = 0");
		}

		println(";");

		if (node.isConstructor()) {
			// Extra init function to handle chained constructor calls
			printi("void _construct(");
			visitAllCSV(node.parameters(), false);

			println(");");
		} else {
			IMethodBinding mb = node.resolveBinding();
			String using = TransformUtil.methodUsing(mb);
			if (using != null) {
				if (state.usings.add(using)) {
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
			addDep((ITypeBinding) b, softDeps);
			print(TransformUtil.relativeCName((ITypeBinding) b, state.tb));
			return false;
		}
		return super.visit(node);
	}

	@Override
	public boolean visit(SimpleType node) {
		ITypeBinding b = node.resolveBinding();
		addDep(b, softDeps);
		print(TransformUtil.relativeCName(b, state.tb));
		return false;
	}

	@Override
	public boolean visit(QualifiedName node) {
		IBinding b = node.resolveBinding();
		if (b instanceof ITypeBinding) {
			addDep((ITypeBinding) b, softDeps);
			print(TransformUtil.relativeCName((ITypeBinding) b, state.tb));
			return false;
		}
		return super.visit(node);
	}

	@Override
	public boolean visit(QualifiedType node) {
		ITypeBinding b = node.resolveBinding();
		addDep(b, softDeps);
		print(TransformUtil.relativeCName(b, state.tb));
		return false;
	}

	@Override
	public boolean visit(SingleVariableDeclaration node) {
		ITypeBinding tb = node.getType().resolveBinding();

		if (node.getExtraDimensions() > 0) {
			addDep(tb, softDeps);
			tb = tb.createArrayType(node.getExtraDimensions());
			print(TransformUtil.relativeCName(tb, state.tb));
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
		State old = state;

		try {
			ITypeBinding tb = node.resolveBinding();

			setOut(TransformUtil.openHeader(root, tb));

			state = new State(tb, getOut());

			if (pkg.getJavadoc() != null) {
				pkg.getJavadoc().accept(this);
			}

			println(TransformUtil.annotations(pkg.annotations()));

			List<ITypeBinding> bases = TransformUtil
					.getBases(node.getAST(), tb);

			hardDeps.addAll(bases);

			for (ITypeBinding base : bases) {
				println(TransformUtil.include(base));
			}

			println();

			println("using namespace java::lang;");

			println();

			if (node.getJavadoc() != null) {
				node.getJavadoc().accept(this);
			}

			print("class ");

			pkg.getName().accept(this);
			print("::");
			node.getName().accept(this);

			print(TransformUtil.typeParameters(node.typeParameters()));

			String sep = " : public ";

			for (ITypeBinding base : bases) {
				print(sep, TransformUtil.inherit(base),
						TransformUtil.relativeCName(base, tb));
				sep = ", public ";
			}

			println();
			printlni("{");

			indent++;

			if (!node.isInterface()) {
				printi("typedef ");
				if (node.getSuperclassType() != null) {
					node.getSuperclassType().accept(this);
				} else {
					print("java::lang::Object");
				}
				println(" super;");
			}

			visitAll(node.bodyDeclarations());

			indent--;

			printlni("};");

			getOut().close();

			addType(tb);
		} catch (IOException e) {
			throw new Error(e);
		}

		state = old;
		if (state != null) {
			setOut(state.out);
		}

		return false;
	}

	/** Write a header for a local or anonymous class */
	public void writeAnonymousHeader(AST ast, ITypeBinding tb,
			List<ImportDeclaration> imports,
			List<BodyDeclaration> declarations,
			Collection<IVariableBinding> closures) {

		if (!tb.isLocal()) {
			System.out.print("Not a local class: " + tb.getKey());
			return;
		}

		State old = state;
		int oldIndent = indent;
		indent = 0;
		try {
			setOut(TransformUtil.openHeader(root, tb));
			state = new State(tb, getOut());

			List<ITypeBinding> bases = TransformUtil.getBases(ast, tb);

			hardDeps.addAll(bases);

			for (ITypeBinding base : bases) {
				println(TransformUtil.include(base));
			}

			println();

			println("using namespace java::lang;");

			println();

			print("class ");

			print(TransformUtil.qualifiedCName(tb));

			String sep = " : public ";

			for (ITypeBinding base : bases) {
				print(sep);
				sep = ", public ";
				print(TransformUtil.inherit(base));
				print(TransformUtil.relativeCName(base, tb));
				addDep(base, hardDeps);
			}

			println();
			printlni("{");

			indent++;

			if (!tb.isInterface()) {
				printi("typedef ");
				if (tb.getSuperclass() != null) {
					print(TransformUtil.relativeCName(tb.getSuperclass(), tb));
				} else {
					print("java::lang::Object");
				}

				println(" super;");
			}

			visitAll(declarations);

			println("public:");
			if (!Modifier.isStatic(tb.getModifiers())) {
				printi(TransformUtil.relativeCName(tb.getDeclaringClass(), tb));
				println(" *", TransformUtil.name(tb.getDeclaringClass()),
						"_this;");
			}

			for (IVariableBinding closure : closures) {
				printi(TransformUtil.relativeCName(closure.getType(), tb));
				print(" ", TransformUtil.ref(closure.getType()),
						closure.getName(), "_;");
			}

			if (tb.isLocal()) {
				// For local classes, synthesize base class constructors
				String name = TransformUtil.name(tb);
				for (IMethodBinding mb : tb.getSuperclass()
						.getDeclaredMethods()) {
					if (!mb.isConstructor()) {
						continue;
					}

					printi(name, "(");

					sep = "";
					if (!Modifier.isStatic(tb.getModifiers())) {
						print(TransformUtil.relativeCName(
								tb.getDeclaringClass(), tb));
						print(" *", TransformUtil.name(tb.getDeclaringClass()),
								"_this");
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

					println(");");
				}
			}
			indent--;

			printlni("};");

			getOut().close();

			addType(tb);
		} catch (IOException e) {
			throw new Error(e);
		}

		state = old;
		if (state != null) {
			setOut(state.out);
		}

		indent = oldIndent;
	}

	@Override
	public boolean visit(TypeLiteral node) {
		node.getType().accept(this);
		print(".class");
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
		ITypeBinding tb = node.resolveBinding().getType();
		addDep(tb, softDeps);
		print(TransformUtil.ref(tb));

		node.getName().accept(this);
		Object v = TransformUtil.constantValue(node);
		if (v != null) {
			print(" = ", v);
		}

		return false;
	}
}
