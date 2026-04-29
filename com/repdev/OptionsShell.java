package com.repdev;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * OptionsShell version 2
 * @author Ryan Schultz
 *
 *  I created this because the current (previous?) dialog has gotten crowded
 *  and has outgrown its original simple functionality.  This new one features
 *  a tabbed view to allow for more options.
 *
 *  F2 (run #7): all four tabs extracted to {@code com.repdev.options.*}.
 *  This class is now a thin orchestrator that wires the {@link CTabFolder},
 *  the OK/Cancel buttons, and routes Save through each tab's own
 *  {@code save()} method. Cancel uses {@link com.repdev.options.OptionsShellSnapshot}
 *  to revert any live-applied state (theme, line-guide, line-numbers,
 *  large-icons).
 */
public class OptionsShell {
	private Shell shell;
	private static OptionsShell me = new OptionsShell();

	private CTabFolder tabs;
	private com.repdev.options.ServerOptionsTab serverTab;
	private com.repdev.options.EditorOptionsTab editorTab;
	private com.repdev.options.DeveloperOptionsTab devTab;
	private com.repdev.options.OptionsShellSnapshot snapshot;

	public static void show(Shell parent) {
		// Snapshot live-applied settings so Cancel can revert them.
		me.snapshot = com.repdev.options.OptionsShellSnapshot.capture();
		me.create(parent);
		me.shell.open();

		Display display = me.shell.getDisplay();
		while (!me.shell.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}
	}

	private void create(Shell parent) {
		shell = new Shell(parent, SWT.CLOSE | SWT.TITLE | SWT.APPLICATION_MODAL);
		shell.setText("Settings");
		shell.setImage(RepDevMain.smallOptionsImage);
		shell.setMinimumSize(480, 380);
		tabs = new CTabFolder(shell, SWT.BORDER);
		tabs.setSimple(false);
		tabs.setTabHeight(24);

		// Tab order is preserved verbatim from the pre-extraction layout.
		serverTab = new com.repdev.options.ServerOptionsTab(tabs, shell);
		editorTab = new com.repdev.options.EditorOptionsTab(tabs, shell);
		new com.repdev.options.DocumentationOptionsTab(tabs, shell);
		if (RepDevMain.DEVELOPER) {
			devTab = new com.repdev.options.DeveloperOptionsTab(tabs);
		}
		tabs.setSelection(0);

		Button cancel = new Button(shell, SWT.PUSH);
		cancel.setText("Cancel");
		cancel.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (snapshot != null) snapshot.revert();
				shell.close();
			}
		});

		Button ok = new Button(shell, SWT.PUSH);
		ok.setText("Save Settings");
		ok.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				// Editor tab validates hex inputs first; on validation failure
				// it shows the message box, resets the offending field to its
				// default, and returns false so we leave the dialog open for
				// the user to correct.
				if (editorTab != null && !editorTab.save()) return;
				if (serverTab != null) serverTab.save();
				if (devTab != null) devTab.save();
				// Theme + follow-system already applied live via EditorOptionsTab listeners.
				shell.close();
			}
		});

		// Layout the shell
		GridLayout layout = new GridLayout();
		layout.marginWidth = 12;
		layout.marginHeight = 12;
		layout.horizontalSpacing = 10;
		layout.verticalSpacing = 10;
		layout.numColumns = 2;
		shell.setLayout(layout);

		GridData data;
		data = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
		tabs.setLayoutData(data);

		data = new GridData(SWT.END, SWT.FILL, false, false, 1, 1);
		ok.setLayoutData(data);

		data = new GridData(SWT.END, SWT.FILL, true, false, 1, 1);
		cancel.setLayoutData(data);

		shell.setDefaultButton(ok);
		com.repdev.theme.ShellTheme.apply(shell, com.repdev.theme.ThemeService.getInstance().getCurrent());
		shell.pack();
	}
}
