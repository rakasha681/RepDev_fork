/**
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 */

package com.repdev.options;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;

import com.repdev.Config;
import com.repdev.RepDevMain;

/**
 * Developer tab of the Options dialog. Gated behind {@link RepDevMain#DEVELOPER}
 * by the caller — this class itself does no gating.
 *
 * <p>F2 step 2: first per-tab extraction. The Developer tab is the smallest and
 * has no live-applied controls, which makes it the cleanest proof of the
 * pattern: a {@link Composite} subclass that owns its widgets and exposes a
 * single {@link #save()} method to commit them. Cancel naturally needs no
 * revert because nothing is applied until {@code save()} runs.</p>
 *
 * <p>The two controls owned here:</p>
 * <ul>
 *   <li>{@code Forget Passwords on exit} → {@link RepDevMain#FORGET_PASS_ON_EXIT}</li>
 *   <li>{@code Enable project file backup} → {@link Config#setBackupProjectFile(boolean)}</li>
 * </ul>
 */
public final class DeveloperOptionsTab {

	private final Composite root;
	private final Button devForgetBox;
	private final Button backupEnable;

	public DeveloperOptionsTab(CTabFolder tabs) {
		root = new Composite(tabs, SWT.NONE);
		CTabItem item = new CTabItem(tabs, SWT.NONE);
		item.setText("Developer");
		item.setControl(root);

		FormLayout layout = new FormLayout();
		layout.marginTop = 5;
		layout.marginBottom = 5;
		layout.marginLeft = 5;
		layout.marginRight = 5;
		layout.spacing = 5;
		root.setLayout(layout);

		Group devGroup = new Group(root, SWT.NONE);
		devGroup.setText("Developer Options");
		devGroup.setLayout(layout);

		Label devNotice = new Label(devGroup, SWT.NONE);
		devNotice.setText("Developer mode enabled");

		devForgetBox = new Button(devGroup, SWT.CHECK);
		devForgetBox.setText("Forget Passwords on exit");
		devForgetBox.setSelection(RepDevMain.FORGET_PASS_ON_EXIT);

		Group devBackup = new Group(root, SWT.NONE);
		devBackup.setText("Backup Options");
		devBackup.setLayout(new GridLayout(2, false));

		backupEnable = new Button(devBackup, SWT.CHECK);
		backupEnable.setText("Enable project file backup");
		backupEnable.setSelection(Config.getBackupProjectFiles());

		FormData data = new FormData();
		data.left = new FormAttachment(0);
		data.right = new FormAttachment(100);
		data.top = new FormAttachment(0);
		devGroup.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(0);
		devNotice.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(devNotice);
		devForgetBox.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.right = new FormAttachment(100);
		data.top = new FormAttachment(devGroup);
		devBackup.setLayoutData(data);
	}

	/** Persist the controls' current state to {@link Config} / {@link RepDevMain}. */
	public void save() {
		RepDevMain.FORGET_PASS_ON_EXIT = devForgetBox.getSelection();
		Config.setBackupProjectFile(backupEnable.getSelection());
	}
}
