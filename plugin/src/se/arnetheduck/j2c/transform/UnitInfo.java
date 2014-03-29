package se.arnetheduck.j2c.transform;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jdt.core.dom.ITypeBinding;

/** Contextual information about a CompilationUnit */
public class UnitInfo {
	public final Map<ITypeBinding, TypeInfo> types = new LinkedHashMap<ITypeBinding, TypeInfo>();
}
