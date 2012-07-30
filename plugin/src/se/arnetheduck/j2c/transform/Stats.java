package se.arnetheduck.j2c.transform;

import org.eclipse.jdt.core.dom.ITypeBinding;

public class Stats {
	private enum Nesting {
		TOP_LEVEL, NESTED, LOCAL, ANONYMOUS
	}

	private enum Type {
		CLASS, INTERFACE, ENUM, ANNOTATION,
	}

	public int stats[][] = new int[Nesting.values().length][Type.values().length];

	public void add(ITypeBinding tb) {
		if (tb.isPrimitive() || tb.isArray()) {
			return;
		}

		Nesting nesting = Nesting.TOP_LEVEL;
		if (tb.isAnonymous()) {
			nesting = Nesting.ANONYMOUS;
		} else if (tb.isLocal()) {
			nesting = Nesting.LOCAL;
		} else if (tb.isNested()) {
			nesting = Nesting.NESTED;
		} else {
			if (!tb.isTopLevel()) {
				System.out.println(tb + " nesting?");
			}
		}

		Type type = Type.CLASS;
		if (tb.isAnnotation()) {
			type = Type.ANNOTATION;
		} else if (tb.isEnum()) {
			type = Type.ENUM;
		} else if (tb.isInterface()) {
			type = Type.INTERFACE;
		} else {
			if (!tb.isClass()) {
				System.out.println(tb + " type?");
			}
		}

		stats[nesting.ordinal()][type.ordinal()]++;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("%15s", ""));
		;
		for (Nesting n : Nesting.values()) {
			sb.append(String.format("%15s", n));
		}

		sb.append('\n');
		for (Type t : Type.values()) {
			sb.append(String.format("%15s", t));
			for (Nesting n : Nesting.values()) {
				sb.append(String.format("%15s", stats[n.ordinal()][t.ordinal()]));
			}

			sb.append('\n');
		}

		return sb.toString();
	}
}
