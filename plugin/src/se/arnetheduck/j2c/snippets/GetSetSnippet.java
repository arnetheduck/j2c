package se.arnetheduck.j2c.snippets;

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

import se.arnetheduck.j2c.transform.EmptySnippet;
import se.arnetheduck.j2c.transform.StubWriter;
import se.arnetheduck.j2c.transform.TransformUtil;
import se.arnetheduck.j2c.transform.Transformer;

public class GetSetSnippet extends EmptySnippet {

	@Override
	public boolean body(Transformer ctx, StubWriter w, IMethodBinding mb) {
		String name = mb.getName();
		if (name.length() < 4)
			return true;

		boolean getter = name.startsWith("get");
		boolean setter = name.startsWith("set");
		if (!getter && !setter)
			return true;

		char[] vname = name.substring(3).toCharArray();
		vname[0] = Character.toLowerCase(vname[0]);
		ITypeBinding tb = mb.getDeclaringClass();
		if (tb == null)
			return true;

		String v = new String(vname);
		boolean ms = TransformUtil.isStatic(mb);

		for (IVariableBinding vb : tb.getDeclaredFields()) {
			if (ms == TransformUtil.isStatic(vb) && vb.getName().equals(v)) {
				boolean asMethod = TransformUtil.asMethod(vb);
				if (getter
						&& mb.getReturnType().isAssignmentCompatible(
								vb.getType())) {
					w.println("return " + v + (asMethod ? "_()" : "_")
							+ " ; /* getter */");
				} else if (setter
						&& mb.getReturnType().getName().equals("void")
						&& mb.getParameterTypes().length == 1
						&& vb.getType().isAssignmentCompatible(
								mb.getParameterTypes()[0])) {
					w.print(TransformUtil.indent(1));
					if (ms) {
						w.print(TransformUtil.name(tb) + "::");
					} else {
						w.print("this->");
					}

					w.println(v + (asMethod ? "_()" : "_")
							+ " = a0; /* setter */");
				}

				return false;
			}
		}

		return true;
	}
}
