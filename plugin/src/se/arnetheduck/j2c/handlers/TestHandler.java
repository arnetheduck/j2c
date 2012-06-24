package se.arnetheduck.j2c.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.JavaCore;

public class TestHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot root = workspace.getRoot();
		// Get all projects in the workspace
		IProject[] projects = root.getProjects();
		// Loop over all projects
		for (IProject project : projects) {
			try {
				if (!project.isOpen()
						|| !project
								.isNatureEnabled("org.eclipse.jdt.core.javanature")) {
					continue;
				}

				if (!project.getName().endsWith("j2c.test")) {
					continue;
				}

				HandlerHelper.process(JavaCore.create(project));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}
}
