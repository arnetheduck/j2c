package se.arnetheduck.j2c.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jface.dialogs.MessageDialog;

import se.arnetheduck.j2c.transform.Transformer;

public class HandlerHelper {
	public static void process(IJavaProject project) throws Exception {
		List<ICompilationUnit> units = new ArrayList<ICompilationUnit>();
		IPackageFragment[] packages = project.getPackageFragments();
		for (IPackageFragment mypackage : packages) {
			if (mypackage.getKind() == IPackageFragmentRoot.K_SOURCE) {
				for (ICompilationUnit u : mypackage.getCompilationUnits()) {
					units.add(u);
				}
			}
		}

		process(project, units);
	}

	public static void process(IPackageFragment mypackage) throws Exception {
		List<ICompilationUnit> units = new ArrayList<ICompilationUnit>();
		if (mypackage.getKind() == IPackageFragmentRoot.K_SOURCE) {
			for (ICompilationUnit u : mypackage.getCompilationUnits()) {
				units.add(u);
			}
		}
		process(mypackage.getJavaProject(), units);
	}

	public static void process(IJavaProject project, ICompilationUnit unit)
			throws Exception {
		List<ICompilationUnit> units = new ArrayList<ICompilationUnit>();

		if (unit == null) {
		} else {
			units.add(unit);
		}

		process(project, units);
	}

	private static void process(final IJavaProject project,
			final List<ICompilationUnit> units) throws Exception {
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

	public static void process(ICompilationUnit unit) throws Exception {
		process(unit.getJavaProject(), Arrays.asList(unit));
	}
}
