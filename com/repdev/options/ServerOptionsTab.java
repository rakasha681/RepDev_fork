/**
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 */

package com.repdev.options;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.repdev.Config;
import com.repdev.RepDevMain;
import com.repdev.RepDev_SSO;

/**
 * Server tab of the Options dialog. Owns Symitar connection settings
 * (server / port / SSO) and keep-alive options (never-terminate / terminate
 * time).
 *
 * <p>F2 step 4: save-only tab — no live-applied controls, so no revert. The
 * SSO toggle's master-password / clear-passwords side effects fire <i>during</i>
 * the listener (matches prior behaviour); only the {@code ssoPass} string is
 * deferred to {@link #save()}.</p>
 */
public final class ServerOptionsTab {

	private final Composite root;
	private final Text serverText;
	private final Text portText;
	private final Button useSSO;
	private final Button neverTerm;
	private final Combo hour;
	private final Combo minute;
	private String ssoPass = "";

	public ServerOptionsTab(CTabFolder tabs, final Shell parentShell) {
		root = new Composite(tabs, SWT.NONE);

		CTabItem tabItem = new CTabItem(tabs, SWT.NONE);
		tabItem.setText("Server Options");
		tabItem.setControl(root);

		Group serverGroup = new Group(root, SWT.NONE);
		serverGroup.setText("Symitar Connection Options");
		FormLayout layout = new FormLayout();
		layout.marginTop = 5;
		layout.marginBottom = 5;
		layout.marginLeft = 5;
		layout.marginRight = 5;
		layout.spacing = 5;
		serverGroup.setLayout(layout);

		FormLayout rootLayout = new FormLayout();
		rootLayout.marginTop = 5;
		rootLayout.marginBottom = 5;
		rootLayout.marginLeft = 5;
		rootLayout.marginRight = 5;
		rootLayout.spacing = 5;
		root.setLayout(rootLayout);

		Label serverLabel = new Label(serverGroup, SWT.NONE);
		serverLabel.setText("Symitar Server IP Address:");

		serverText = new Text(serverGroup, SWT.SINGLE | SWT.BORDER);
		serverText.setText(Config.getServer());

		Label portLabel = new Label(serverGroup, SWT.NONE);
		portLabel.setText("Port (22 - SSH , 23 - Telnet)");

		portText = new Text(serverGroup, SWT.SINGLE | SWT.BORDER);
		portText.setText("" + Config.getPort());

		Label useSSOLabel = new Label(serverGroup, SWT.NONE);
		useSSOLabel.setText("Use Single Sign-On");

		useSSO = new Button(serverGroup, SWT.CHECK);
		useSSO.setSelection(Config.useSSO());
		useSSO.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (useSSO.getSelection()) {
					String password = RepDev_SSO.getRepDevPassword(parentShell);
					if (password.contentEquals("")) {
						useSSO.setSelection(false);
						ssoPass = "";
					} else {
						ssoPass = password;
						RepDevMain.MASTER_PASSWORD_HASH = RepDev_SSO.md5Hash(ssoPass);
						Config.setPasswordValidator(ssoPass);
					}
				} else {
					MessageBox dialog = new MessageBox(parentShell, SWT.OK | SWT.CANCEL | SWT.ICON_WARNING);
					dialog.setText("Single Sign-On");
					dialog.setMessage("All of the cached passwords will be deleted IMMEDIATELY ! ! !");
					int selection = dialog.open();

					if (selection == SWT.OK) {
						System.out.println("ALL Passwords deleted");
						for (int sym : RepDevMain.SESSION_INFO.keySet()) {
							RepDevMain.SESSION_INFO.get(sym).clearCredential();
						}
						ssoPass = "";
						Config.setPasswordValidator("");
						RepDevMain.MASTER_PASSWORD_HASH = null;
					} else {
						System.out.println("ALL Passwords delete cancelled");
						useSSO.setSelection(true);
					}
				}
			}
		});

		Group keepAliveGroup = new Group(root, SWT.NONE);
		keepAliveGroup.setText("Keep Alive Options (Log out Sym Required)");
		FormLayout keepLayout = new FormLayout();
		keepLayout.marginTop = 5;
		keepLayout.marginBottom = 5;
		keepLayout.marginLeft = 5;
		keepLayout.marginRight = 5;
		keepAliveGroup.setLayout(keepLayout);

		Label neverTermLabel = new Label(keepAliveGroup, SWT.NONE);
		neverTermLabel.setText("Never Terminate");

		neverTerm = new Button(keepAliveGroup, SWT.CHECK);
		neverTerm.setSelection(Config.getNeverTerminate());
		neverTerm.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				boolean enabled = !neverTerm.getSelection();
				hour.setEnabled(enabled);
				minute.setEnabled(enabled);
			}
		});

		Label keepAliveLabel = new Label(keepAliveGroup, SWT.NONE);
		keepAliveLabel.setText("Terminate Time (HH:MM)");
		hour = new Combo(keepAliveGroup, SWT.READ_ONLY);
		for (int i = 0; i < 24; i++) {
			hour.add(((i + 1) < 10 ? "0" : "") + Integer.toString(i + 1), i);
		}
		hour.select(Config.getTerminateHour() - 1);
		Label colon = new Label(keepAliveGroup, SWT.NONE);
		colon.setText(" : ");
		minute = new Combo(keepAliveGroup, SWT.READ_ONLY);
		for (int i = 0; i < 6; i++) {
			minute.add(((i * 10) < 10 ? "0" : "") + Integer.toString(i * 10), i);
		}
		minute.select(Config.getTerminateMinute() / 10);

		boolean enabled = !neverTerm.getSelection();
		hour.setEnabled(enabled);
		minute.setEnabled(enabled);

		// Layout grid data — preserved verbatim from the pre-extraction layout.
		FormData data = new FormData();
		data.left = new FormAttachment(0);
		data.right = new FormAttachment(100);
		data.top = new FormAttachment(0);
		data.bottom = new FormAttachment(keepAliveGroup);
		serverGroup.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.right = new FormAttachment(100);
		data.top = new FormAttachment(serverGroup);
		keepAliveGroup.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(0);
		data.width = 140;
		serverLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(serverLabel);
		data.top = new FormAttachment(0);
		data.right = new FormAttachment(100);
		data.width = 140;
		serverText.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(serverText);
		data.width = 140;
		portLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(portLabel);
		data.top = new FormAttachment(serverText);
		data.right = new FormAttachment(100);
		data.width = 140;
		portText.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(portLabel);
		data.width = 140;
		useSSOLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(useSSOLabel);
		data.top = new FormAttachment(portLabel);
		data.height = 25;
		useSSO.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(0);
		data.width = 160;
		neverTermLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(neverTermLabel);
		data.top = new FormAttachment(0);
		neverTerm.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(neverTerm, 4);
		data.width = 160;
		keepAliveLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(keepAliveLabel);
		data.top = new FormAttachment(neverTerm, 4);
		data.width = 10;
		hour.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(hour);
		data.top = new FormAttachment(neverTerm, 4);
		colon.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(colon);
		data.top = new FormAttachment(neverTerm, 4);
		data.width = 10;
		minute.setLayoutData(data);
	}

	/** Persist server / port / SSO / keep-alive controls to {@link Config}. */
	public void save() {
		Config.setTerminateHour(hour.getSelectionIndex() + 1);
		Config.setTerminateMinute(minute.getSelectionIndex() * 10);
		Config.setNeverTerminate(neverTerm.getSelection());
		if (!ssoPass.contentEquals("")) {
			Config.setPasswordValidator(ssoPass);
		}
		Config.setServer(serverText.getText());
		Config.setPort(Integer.parseInt(portText.getText()));
	}
}
