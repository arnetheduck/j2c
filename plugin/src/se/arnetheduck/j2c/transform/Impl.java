package se.arnetheduck.j2c.transform;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Modifier;

public class Impl {
	private final ITypeBinding type;
	private final Transformer ctx;
	private final Collection<ITypeBinding> softDeps;
	private final Collection<ITypeBinding> hardDeps;

	private final String qcname;

	private static final String i1 = TransformUtil.indent(1);
	private static final String i2 = TransformUtil.indent(2);

	private PrintWriter pw;
	private boolean isNative;

	public Impl(Transformer ctx, ITypeBinding type,
			Collection<ITypeBinding> softDeps, Collection<ITypeBinding> hardDeps) {
		this.ctx = ctx;
		this.type = type;
		this.softDeps = softDeps;
		this.hardDeps = hardDeps;

		qcname = CName.qualified(type, true);
	}

	public void write(IPath root, String body, String suffix,
			Collection<IVariableBinding> closures, StringWriter clinit,
			boolean fmod, boolean isNative) throws IOException {

		this.isNative = isNative;
		String extras = getExtras(closures, clinit);

		FileOutputStream fos = TransformUtil.open(TransformUtil.implPath(root,
				type, suffix).toFile());

		pw = new PrintWriter(fos);

		if (type.getJavaElement() != null) {
			pw.println("// Generated from " + type.getJavaElement().getPath());
		} else {
			pw.println("// Generated");
		}

		printIncludes(fmod);

		pw.print(body);
		pw.print(extras);

		pw.close();
		pw = null;
	}

	private String getExtras(Collection<IVariableBinding> closures,
			StringWriter clinit) {
		StringWriter sw = new StringWriter();
		pw = new PrintWriter(sw);
		printClassLiteral();
		printClinit(clinit);
		printSuperCalls();

		printDtor();
		printGetClass();

		pw.close();
		pw = null;
		return sw.toString();
	}

	private void printIncludes(boolean fmod) {
		pw.println(TransformUtil.include(type));
		pw.println();

		boolean hasInc = false;
		boolean hasArray = false;

		for (ITypeBinding dep : hardDeps) {
			if (dep.isEqualTo(type)) {
				continue;
			}

			if (dep.isNullType() || dep.isPrimitive()) {
				continue;
			}

			if (TransformUtil.isPrimitiveArray(dep)) {
				if (hasArray) {
					continue;
				}

				hasArray = true;
			}

			pw.println(TransformUtil.include(dep));
			hasInc = true;
		}

		if (fmod) {
			pw.println("#include <cmath>");
			hasInc = true;
		}

		if (hasInc) {
			pw.println();
		}
	}

	private void printClinit(StringWriter clinit) {
		if (isNative || !type.isClass() && !type.isEnum()) {
			return;
		}

		pw.println("void " + qcname + "::" + CName.STATIC_INIT + "()");
		pw.println("{");

		if (type.getSuperclass() != null) {
			pw.println(i1 + "super::" + CName.STATIC_INIT + "();");
		}

		if (clinit != null) {
			pw.println("struct clinit_ {");
			pw.println(i1 + "clinit_() {");
			pw.print(clinit.toString());
			pw.println(i1 + "}");
			pw.println("};");
			pw.println();
			pw.println("static clinit_ clinit_instance;");
		}

		pw.println("}");
		pw.println();
	}

	private void printClassLiteral() {
		if (isNative) {
			return;
		}

		pw.println("extern ::java::lang::Class *class_(const char16_t *c, int n);");
		pw.println();
		if (type.isArray() && type.getComponentType().isPrimitive()) {
			pw.println("template<>");
		}
		pw.println("::java::lang::Class *" + qcname + "::class_()");
		pw.println("{");
		pw.println("    static ::java::lang::Class *c = ::class_(u\""
				+ type.getQualifiedName() + "\", "
				+ type.getQualifiedName().length() + ");");
		pw.println("    return c;");

		pw.println("}");
		pw.println();
	}

	private void printGetClass() {
		if (isNative || !type.isClass() && !type.isEnum() && !type.isArray()) {
			return;
		}

		if (type.isArray() && type.getComponentType().isPrimitive()) {
			pw.println("template<>");
		}

		pw.println("::java::lang::Class *" + qcname + "::" + CName.GET_CLASS
				+ "()");
		pw.println("{");
		pw.println(i1 + "return class_();");
		pw.println("}");
		pw.println();
	}

	private void printDtor() {
		if (isNative || !TransformUtil.same(type, Object.class)) {
			return;
		}
		pw.println("::java::lang::Object::~Object()");
		pw.println("{");
		pw.println("}");
		pw.println();
	}

	private void printSuperCalls() {
		if (isNative || !type.isClass() && !type.isEnum()) {
			return;
		}

		List<IMethodBinding> missing = Header.baseCallMethods(type);
		for (IMethodBinding decl : missing) {
			IMethodBinding impl = Header.findImpl(type, decl);
			if (impl == null) {
				// Only print super call if an implementation actually
				// exists
				continue;
			}

			if (Modifier.isAbstract(impl.getModifiers())) {
				continue;
			}

			printSuperCall(decl, impl);
		}
	}

	private void printSuperCall(IMethodBinding decl, IMethodBinding impl) {
		IMethodBinding md = decl.getMethodDeclaration();
		TransformUtil.printSignature(pw, type, md, impl.getReturnType(),
				softDeps, true);
		pw.println();
		pw.println("{");

		boolean erased = TransformUtil.returnErased(impl);

		if (TransformUtil.isVoid(impl.getReturnType())) {
			pw.format(i1 + "%s::%s(", CName.of(impl.getDeclaringClass()),
					CName.of(impl));
		} else {
			pw.print(i1 + "return ");
			if (erased) {
				dynamicCast(impl.getMethodDeclaration().getReturnType()
						.getErasure(), impl.getReturnType());
			}

			pw.format("%s::%s(", CName.of(impl.getDeclaringClass()),
					CName.of(impl));
		}

		String sep = "";
		for (int i = 0; i < impl.getParameterTypes().length; ++i) {
			pw.print(sep);
			sep = ", ";

			boolean cast = !md.getParameterTypes()[i].getErasure()
					.isAssignmentCompatible(
							impl.getParameterTypes()[i].getErasure());
			if (cast) {
				dynamicCast(md.getParameterTypes()[i],
						impl.getParameterTypes()[i]);
			}
			pw.print(TransformUtil.paramName(md, i));
			if (cast) {
				pw.print(")");
			}
		}

		if (erased) {
			pw.print(")");
		}

		pw.println(");");
		pw.println("}");
		pw.println();
	}

	private void dynamicCast(ITypeBinding source, ITypeBinding target) {
		hardDep(source);
		hardDep(target);
		pw.print("dynamic_cast< " + CName.relative(target, type, true) + "* >(");
	}

	private void hardDep(ITypeBinding dep) {
		TransformUtil.addDep(dep, hardDeps);
		ctx.hardDep(dep);
	}

}
