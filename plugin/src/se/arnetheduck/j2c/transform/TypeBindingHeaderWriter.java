package se.arnetheduck.j2c.transform;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

public class TypeBindingHeaderWriter {
	private Set<ITypeBinding> types = new HashSet<ITypeBinding>();
	private Set<ITypeBinding> hardDeps = new HashSet<ITypeBinding>();
	private Set<ITypeBinding> softDeps = new HashSet<ITypeBinding>();
	private final IPath root;
	private final ITypeBinding object;

	public TypeBindingHeaderWriter(ITypeBinding object, IPath root) {
		this.object = object;
		this.root = root;
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

	public void write(ITypeBinding tb) throws IOException {
		if (tb.isArray()) {
			return;
		}

		printClass(tb);
	}

	private void printClass(ITypeBinding tb) throws IOException {
		if (types.contains(tb)) {
			return;
		}

		types.add(tb);

		for (ITypeBinding nb : tb.getDeclaredTypes()) {
			printClass(nb);
		}

		PrintWriter pw = TransformUtil.openHeader(root, tb);

		List<ITypeBinding> bases = TransformUtil.getBases(tb, object);
		for (ITypeBinding b : bases) {
			pw.println(TransformUtil.include(b));
		}

		pw.print("class ");
		pw.print(TransformUtil.qualifiedCName(tb));

		String sep = " : public ";
		for (ITypeBinding b : bases) {
			TransformUtil.addDep(b, hardDeps);

			pw.print(sep);
			sep = ", public ";
			pw.print(TransformUtil.inherit(b));
			pw.print(TransformUtil.qualifiedCName(b));
		}

		if (bases.isEmpty()
				&& !tb.getQualifiedName().equals(Object.class.getName())) {
			pw.print(" : public virtual java::lang::Object");
		}

		pw.println(" {");

		if (tb.getSuperclass() != null) {
			pw.print(TransformUtil.indent(1));
			pw.print("typedef ");
			pw.print(TransformUtil.qualifiedCName(tb.getSuperclass()));
			pw.println(" super;");
		}

		String lastAccess = null;
		for (IVariableBinding vb : tb.getDeclaredFields()) {
			lastAccess = TransformUtil.printAccess(pw, vb.getModifiers(),
					lastAccess);
			printField(pw, vb);
		}

		pw.println();

		Set<String> usings = new HashSet<String>();

		for (IMethodBinding mb : tb.getDeclaredMethods()) {
			lastAccess = TransformUtil.printAccess(pw, mb.getModifiers(),
					lastAccess);
			printMethod(pw, tb, mb, usings);
		}

		if (tb.getQualifiedName().equals("java.lang.String")) {
			pw.print("int compareTo(Object* o);");
		}
		pw.println("};");

		if (tb.getQualifiedName().equals("java.lang.String")) {
			pw.println("java::lang::String *join(java::lang::String *lhs, java::lang::String *rhs);");
			for (String type : new String[] { "java::lang::Object *", "bool ",
					"int8_t ", "wchar_t ", "double ", "float ", "int32_t ",
					"int64_t ", "int16_t " }) {
				pw.println("java::lang::String *join(java::lang::String *lhs, "
						+ type + "rhs);");
				pw.println("java::lang::String *join(" + type
						+ "lhs, java::lang::String *rhs);");
			}
		}

		pw.close();
	}

	private void printField(PrintWriter pw, IVariableBinding vb) {
		TransformUtil.addDep(vb.getType(), softDeps);

		pw.print(TransformUtil.indent(1));

		Object constant = TransformUtil.constantValue(vb);
		pw.print(TransformUtil.fieldModifiers(vb.getModifiers(), true,
				constant != null));

		pw.print(TransformUtil.qualifiedCName(vb.getType()));
		pw.print(" ");

		pw.print(TransformUtil.ref(vb.getType()));
		pw.print(vb.getName());
		pw.print("_");

		if (constant != null) {
			pw.print(" = ");
			pw.print(constant);
		}

		pw.println(";");
	}

	private void printMethod(PrintWriter pw, ITypeBinding tb,
			IMethodBinding mb, Set<String> usings) {
		if (tb.isInterface() && !checkBases(mb, tb)) {
			return;
		}

		if (Modifier.isPrivate(mb.getModifiers())) {
			pw.println("/* private: xxx " + mb.getName() + "(...) */");
			return;
		}

		pw.print(TransformUtil.indent(1));

		if (!mb.isConstructor()) {
			ITypeBinding rt = mb.getReturnType();
			TransformUtil.addDep(rt, softDeps);

			pw.print(TransformUtil.methodModifiers(mb.getModifiers(),
					tb.getModifiers()));

			pw.print(TransformUtil.qualifiedCName(rt));
			pw.print(" ");
			pw.print(TransformUtil.ref(rt));
		}

		pw.print(mb.isConstructor() ? TransformUtil.name(mb.getDeclaringClass())
				: TransformUtil.keywords(mb.getMethodDeclaration().getName()));

		pw.print("(");
		for (int i = 0; i < mb.getParameterTypes().length; ++i) {
			if (i > 0)
				pw.print(", ");

			ITypeBinding pb = mb.getParameterTypes()[i];
			TransformUtil.addDep(pb, softDeps);

			pw.print(TransformUtil.qualifiedCName(pb));
			pw.print(" ");
			pw.print(TransformUtil.ref(pb));
			pw.print("a" + i);

		}

		pw.print(")");
		if (Modifier.isAbstract(mb.getModifiers())) {
			pw.print(" = 0");
		}

		pw.println(";");

		String using = TransformUtil.methodUsing(mb);
		if (using != null) {
			if (usings.add(using)) {
				pw.print(TransformUtil.indent(1));
				pw.println(using);
			}
		}
	}

	private static boolean checkBases(IMethodBinding mb, ITypeBinding tb) {
		for (ITypeBinding ib : tb.getInterfaces()) {
			for (IMethodBinding imb : ib.getDeclaredMethods()) {
				if (mb.overrides(imb)) {
					return false;
				}
			}

			if (!checkBases(mb, ib)) {
				return false;
			}
		}

		return true;
	}
}
