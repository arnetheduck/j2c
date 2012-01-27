package se.arnetheduck.j2c.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Our sample handler extends AbstractHandler, an IHandler base class.
 * 
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class TransformHandler extends AbstractHandler {
	/**
	 * The constructor.
	 */
	public TransformHandler() {
	}

	/**
	 * the command has been executed, so extract extract the needed information
	 * from the application context.
	 */
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IStructuredSelection selection = (IStructuredSelection) HandlerUtil
				.getActiveMenuSelection(event);

		Object firstElement = selection.getFirstElement();
		if (firstElement instanceof IJavaProject) {
			try {
				HandlerHelper.process((IJavaProject) firstElement);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (firstElement instanceof ICompilationUnit) {
			try {
				HandlerHelper.process((ICompilationUnit) firstElement);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			MessageDialog.openInformation(HandlerUtil.getActiveShell(event),
					"Information",
					"Please select a Java project, package or source file");
		}
		return null;
	}
}
