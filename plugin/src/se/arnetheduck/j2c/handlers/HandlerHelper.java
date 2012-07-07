package se.arnetheduck.j2c.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.handlers.HandlerUtil;

import se.arnetheduck.j2c.transform.Transformer;

public class HandlerHelper {
	public static Collection<ICompilationUnit> units(IJavaProject project)
			throws JavaModelException {
		List<ICompilationUnit> units = new ArrayList<ICompilationUnit>();
		IPackageFragment[] packages = project.getPackageFragments();
		for (IPackageFragment pf : packages) {
			add(units, pf);
		}

		return units;
	}

	public static Collection<ICompilationUnit> units(IPackageFragmentRoot pfr)
			throws JavaModelException {
		List<ICompilationUnit> units = new ArrayList<ICompilationUnit>();
		for (IJavaElement pf : pfr.getChildren()) {
			if (pf instanceof IPackageFragment) {
				add(units, (IPackageFragment) pf);
			}
		}

		return units;
	}

	public static Collection<ICompilationUnit> units(IPackageFragment pf)
			throws JavaModelException {
		List<ICompilationUnit> units = new ArrayList<ICompilationUnit>();
		add(units, pf);
		return units;
	}

	public static Collection<ICompilationUnit> units(ICompilationUnit unit) {
		List<ICompilationUnit> units = new ArrayList<ICompilationUnit>();

		if (unit != null) {
			units.add(unit);
		}

		return units;
	}

	private static void add(Collection<ICompilationUnit> units,
			IPackageFragment pf) throws JavaModelException {
		if (pf.getKind() == IPackageFragmentRoot.K_SOURCE) {
			add(units, pf.getCompilationUnits());
		}
	}

	private static void add(Collection<ICompilationUnit> units,
			ICompilationUnit... u) {
		units.addAll(Arrays.asList(u));
	}

	public static void process(final IJavaProject project,
			final Collection<ICompilationUnit> units) {
		if (units.isEmpty()) {
			MessageDialog.openInformation(HandlerUtil.getActiveShell(null),
					"No sources", "No Java source code found in selection");
			return;
		}

		// We will write to the source folder prefixed with "c"
		final IPath p = project.getProject().getLocation()
				.removeLastSegments(1)
				.append("c" + project.getProject().getLocation().lastSegment())
				.addTrailingSeparator();

		if (!p.toFile().exists()) {
			MessageDialog.openError(null, "Output directory missing", p
					.toFile().getAbsolutePath()
					+ " does not exist, create it before running plugin.!");
		} else {
			Job job = new Job("C++ translation") {
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					// Set total number of work units
					monitor.beginTask("Translating to C++",
							IProgressMonitor.UNKNOWN);
					try {
						Transformer t = new Transformer(project, project
								.getProject().getName(), p);

						t.process(monitor,
								units.toArray(new ICompilationUnit[0]));
					} catch (Exception e) {
						e.printStackTrace();
						return Status.CANCEL_STATUS;
					}

					return Status.OK_STATUS;
				}
			};

			job.setPriority(Job.BUILD);
			job.schedule();
		}
	}
}
