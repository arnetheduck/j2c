package se.arnetheduck.j2c.transform;

import java.io.IOException;
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
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
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

	public String getImports(List<ImportDeclaration> declarations) {
		StringWriter sw = new StringWriter();

		PrintWriter old = out;
		out = new PrintWriter(sw);
		for (ImportDeclaration node : declarations) {
			printIndent(out);
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
		/* todo */
		return false;
	}

	@Override
	public boolean visit(Block node) {
		if (handledBlocks.contains(node.getParent().getClass())) {
			printIndent(out);
			out.println("{");

			indent++;

			for (Object o : node.statements()) {
				Statement s = (Statement) o;
				s.accept(this);
			}

			indent--;
			printIndent(out);
			out.println("}");
			out.println();
		}

		return false;
	}

	@Override
	public boolean visit(EnumConstantDeclaration node) {
		if (node.getJavadoc() != null) {
			node.getJavadoc().accept(this);
		}

		printIndent(out);

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

		printIndent(out);
		// printModifiers(node.modifiers());
		out.print("enum ");
		node.getName().accept(this);

		if (!node.superInterfaceTypes().isEmpty()) {
			out.print(" implements ");

			visitAllCSV(node.superInterfaceTypes(), false);
		}

		out.print(" {");

		indent++;

		visitAllCSV(node.enumConstants(), false);

		if (!node.bodyDeclarations().isEmpty()) {
			out.print("; ");
			visitAll(node.bodyDeclarations());
		}

		indent--;

		printIndent(out);

		out.print("}\n");
		return false;
	}

	@Override
	public boolean visit(FieldDeclaration node) {
		if (node.getJavadoc() != null) {
			node.getJavadoc().accept(this);
		}

		state.lastAccess = TransformUtil.printAccess(out, node.getModifiers(),
				state.lastAccess);

		List<VariableDeclarationFragment> fragments = node.fragments();

		if (isAnyArray(fragments)) {
			for (VariableDeclarationFragment f : fragments) {
				printIndent(out);

				out.print(TransformUtil.fieldModifiers(node.getModifiers(),
						true, hasInitilializer(fragments)));

				ITypeBinding at = node.getType().resolveBinding()
						.createArrayType(f.getExtraDimensions());

				out.print(TransformUtil.qualifiedCName(at));

				out.print(" ");

				f.accept(this);

				out.println(";");
			}
		} else {
			printIndent(out);

			out.print(TransformUtil.fieldModifiers(node.getModifiers(), true,
					hasInitilializer(fragments)));

			node.getType().accept(this);

			out.print(" ");

			visitAllCSV(fragments, false);

			out.println(";");
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

		state.lastAccess = TransformUtil.printAccess(out, Modifier.PRIVATE,
				state.lastAccess);
		out.println("private:");

		String name = TransformUtil.name(state.tb);
		printIndent(out);
		out.print("struct ");

		out.print(name);

		out.println("Initializer {");
		indent++;

		printIndent(out);
		out.print(name);
		out.println("Initializer();");
		indent--;

		printIndent(out);
		out.println("};");

		printIndent(out);
		out.print("static ");
		out.print(name);
		out.println("Initializer staticInitializer;");

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
	public boolean visit(MethodDeclaration node) {
		state.lastAccess = TransformUtil.printAccess(out, node.getModifiers(),
				state.lastAccess);

		if (node.getJavadoc() != null) {
			node.getJavadoc().accept(this);
		}

		printIndent(out);

		if (!node.isConstructor()) {
			out.print(TransformUtil.methodModifiers(node.getModifiers(),
					state.tb.getModifiers()));
		}

		out.print(TransformUtil.typeParameters(node.typeParameters()));

		if (node.getReturnType2() != null) {
			node.getReturnType2().accept(this);
			out.print(" ");
			out.print(TransformUtil.ref(node.getReturnType2()));
		}

		node.getName().accept(this);

		visitAllCSV(node.parameters(), true);

		out.print(TransformUtil.throwsDecl(node.thrownExceptions()));

		if (node.getBody() == null && !Modifier.isNative(node.getModifiers())) {
			out.print(" = 0");
		}

		out.println(";");

		if (node.isConstructor()) {
			// Extra init function to handle chained constructor calls
			printIndent(out);
			out.print("void _construct(");
			visitAllCSV(node.parameters(), false);

			out.println(");");
		} else {
			IMethodBinding mb = node.resolveBinding();
			String using = TransformUtil.methodUsing(mb);
			if (using != null) {
				if (state.usings.add(using)) {
					printIndent(out);
					out.println(using);
				}
			}
		}

		return false;
	}

	@Override
	public boolean visit(SingleVariableDeclaration node) {
		ITypeBinding tb = node.getType().resolveBinding();
		if (node.getExtraDimensions() > 0) {
			tb = tb.createArrayType(node.getExtraDimensions());
			out.print(TransformUtil.name(tb));
		} else {
			node.getType().accept(this);
		}
		if (node.isVarargs()) {
			out.print("/*...*/");
		}

		out.print(" ");

		out.print(TransformUtil.ref(tb));

		node.getName().accept(this);

		return false;
	}

	@Override
	public boolean visit(TypeDeclaration node) {
		State old = state;

		try {
			ITypeBinding tb = node.resolveBinding();

			out = TransformUtil.openHeader(root, tb);

			state = new State(tb, out);

			if (pkg.getJavadoc() != null) {
				pkg.getJavadoc().accept(this);
			}

			out.println(TransformUtil.annotations(pkg.annotations()));

			List<ITypeBinding> bases = TransformUtil
					.getBases(node.getAST(), tb);

			hardDeps.addAll(bases);

			for (ITypeBinding base : bases) {
				out.println(TransformUtil.include(base));
			}

			out.println();

			out.println("using namespace java::lang;");

			out.println(getImports(imports));

			out.println();

			if (node.getJavadoc() != null) {
				node.getJavadoc().accept(this);
			}

			out.print("class ");

			pkg.getName().accept(this);
			out.print("::");
			node.getName().accept(this);

			out.print(TransformUtil.typeParameters(node.typeParameters()));

			String sep = " : public ";

			for (ITypeBinding base : bases) {
				out.print(sep);
				sep = ", public ";
				out.print(TransformUtil.inherit(base));
				out.print(TransformUtil.qualifiedCName(base));
			}

			out.println();
			printIndent(out);
			out.println("{");

			indent++;

			if (!node.isInterface()) {
				printIndent(out);
				out.print("typedef ");
				if (node.getSuperclassType() != null) {
					node.getSuperclassType().accept(this);
				} else {
					out.print("java::lang::Object");
				}
				out.println(" super;");
			}

			visitAll(node.bodyDeclarations());

			indent--;

			printIndent(out);
			out.println("};");

			out.close();

			addType(tb);
		} catch (IOException e) {
			throw new Error(e);
		}

		state = old;
		if (state != null) {
			out = state.out;
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
			out = TransformUtil.openHeader(root, tb);
			state = new State(tb, out);

			List<ITypeBinding> bases = TransformUtil.getBases(ast, tb);

			hardDeps.addAll(bases);

			for (ITypeBinding base : bases) {
				out.println(TransformUtil.include(base));
			}

			out.println();

			out.println("using namespace java::lang;");

			out.println(getImports(imports));

			out.println();

			out.print("class ");

			out.print(TransformUtil.qualifiedCName(tb));

			String sep = " : public ";

			for (ITypeBinding base : bases) {
				out.print(sep);
				sep = ", public ";
				out.print(TransformUtil.inherit(base));
				out.print(TransformUtil.relativeCName(base, tb));
				addDep(base, hardDeps);
			}

			out.println();
			printIndent(out);
			out.println("{");

			indent++;

			if (!tb.isInterface()) {
				printIndent(out);
				out.print("typedef ");
				if (tb.getSuperclass() != null) {
					out.print(TransformUtil.relativeCName(tb.getSuperclass(),
							tb));
				} else {
					out.print("java::lang::Object");
				}

				out.println(" super;");
			}

			visitAll(declarations);

			out.println("public:");
			if (!Modifier.isStatic(tb.getModifiers())) {
				printIndent(out);
				out.print(TransformUtil.relativeCName(tb.getDeclaringClass(),
						tb));
				out.println(" *" + TransformUtil.name(tb.getDeclaringClass())
						+ "_this;");
			}

			for (IVariableBinding closure : closures) {
				printIndent(out);
				out.print(TransformUtil.relativeCName(closure.getType(), tb));
				out.print(" ");
				out.print(TransformUtil.ref(closure.getType()));
				out.print(closure.getName());
				out.println("_;");
			}

			if (tb.isLocal()) {
				// For local classes, synthesize base class constructors
				String name = TransformUtil.name(tb);
				for (IMethodBinding mb : tb.getSuperclass()
						.getDeclaredMethods()) {
					if (!mb.isConstructor()) {
						continue;
					}

					out.print(TransformUtil.indent(indent));
					out.print(name);

					out.print("(");

					sep = "";
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
						out.print(TransformUtil.ref(closure.getType()));
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

					out.println(");");
				}
			}
			indent--;

			printIndent(out);
			out.println("};");

			out.close();

			addType(tb);
		} catch (IOException e) {
			throw new Error(e);
		}

		state = old;
		if (state != null) {
			out = state.out;
		}

		indent = oldIndent;
	}

	@Override
	public boolean visit(TypeLiteral node) {
		node.getType().accept(this);
		out.print(".class");
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
		ITypeBinding tb = node.resolveBinding().getType();
		addDep(tb, softDeps);
		out.print(TransformUtil.ref(tb));

		node.getName().accept(this);
		Object v = TransformUtil.constantValue(node);
		if (v != null) {
			out.print(" = ");

			out.print(v);
		}

		return false;
	}
}
