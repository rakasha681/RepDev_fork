/**
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 */

package com.repdev.options;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.repdev.RepDevMain;

/**
 * Documentation tab of the Options dialog. Manages the {@code helpmenu.conf}
 * entries surfaced under Help → user-defined links.
 *
 * <p>F2 step 3: extracted as a self-contained Composite. Unlike the other
 * tabs, this one does <b>not</b> participate in the {@code OptionsShell} Save
 * button at all — it writes {@code helpmenu.conf} directly from its own inline
 * Save button (preserving prior behaviour). Cancel correspondingly does
 * <i>not</i> undo helpmenu.conf changes (also pre-existing).</p>
 *
 * <p>That makes this the simplest-possible extraction: no public {@code save()}
 * method, no snapshot, no revert.</p>
 */
public final class DocumentationOptionsTab {

	private final Composite root;

	public DocumentationOptionsTab(CTabFolder tabs, Shell parentShell) {
		root = new Composite(tabs, SWT.NONE);
		CTabItem item = new CTabItem(tabs, SWT.NONE);
		item.setText("Documentation");
		item.setControl(root);

		GridLayout layout = new GridLayout(4, false);
		layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 5;
		root.setLayout(layout);

		Group docGroup = new Group(root, SWT.NONE);
		docGroup.setText("Add Item");
		docGroup.setLayout(new GridLayout(3, false));

		Label nameLabel = new Label(docGroup, SWT.NONE);
		nameLabel.setText("Name");
		final Text name = new Text(docGroup, SWT.SINGLE | SWT.BORDER);

		Label locLabel = new Label(docGroup, SWT.NONE);
		locLabel.setText("Location");
		final Text location = new Text(docGroup, SWT.SINGLE | SWT.BORDER);

		Button browse = new Button(docGroup, SWT.PUSH);
		browse.setText("Browse");
		browse.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				FileDialog dialog = new FileDialog(parentShell, SWT.OPEN);
				String fn = dialog.open();
				if (fn != null) location.setText(fn);
			}
		});

		final List items = new List(root, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);

		// Populate from helpmenu.conf — matches the existing format ("name = location"
		// per line, "----" for separator).
		File docsFile = new File("helpmenu.conf");
		try {
			if (!docsFile.exists()) docsFile.createNewFile();
			BufferedReader docsReader = new BufferedReader(new FileReader(docsFile));
			String line;
			while ((line = docsReader.readLine()) != null) {
				if (line.equals("----")) {
					items.add("----");
					continue;
				}
				String[] data = line.split("=");
				if (data.length != 2) continue;
				items.add(data[0].trim());
				items.setData(data[0].trim(), data[1].trim());
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		items.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (items.getSelectionIndex() == -1) return;
				name.setText(items.getSelection()[0]);
				if (items.getSelection()[0].equals("----")) {
					location.setText("");
					return;
				}
				location.setText((String) items.getData(items.getSelection()[0]));
			}
		});

		Composite upDownGroup = new Composite(root, SWT.NONE);
		upDownGroup.setLayout(new GridLayout());
		Button moveUp = new Button(upDownGroup, SWT.ARROW | SWT.UP);
		Button moveDn = new Button(upDownGroup, SWT.ARROW | SWT.DOWN);

		moveUp.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				int index = items.getSelectionIndex();
				if (index < 1) return;
				String n = items.getItem(index);
				items.remove(index);
				items.add(n, index - 1);
				items.setSelection(index - 1);
			}
		});

		moveDn.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				int index = items.getSelectionIndex();
				if (index >= items.getItemCount() - 1) return;
				if (index == -1) return;
				String n = items.getItem(index);
				items.remove(index);
				items.add(n, index + 1);
				items.setSelection(index + 1);
			}
		});

		Composite addRemGroup = new Composite(root, SWT.NONE);
		addRemGroup.setLayout(new GridLayout(2, false));
		Button addItem = new Button(addRemGroup, SWT.PUSH);
		addItem.setText("Add");
		addItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (name.getText().trim().equals("") || location.getText().trim().equals(""))
					return;
				int index = items.getSelectionIndex();
				if (index == -1) index = items.getItemCount();
				items.add(name.getText(), index);
				items.setData(name.getText(), location.getText());
				name.setText("");
				location.setText("");
			}
		});

		Button remItem = new Button(addRemGroup, SWT.PUSH);
		remItem.setText("Remove");
		remItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (items.getSelectionIndex() == -1) return;
				items.setData(items.getSelection()[0], null);
				items.remove(items.getSelectionIndex());
				name.setText("");
				location.setText("");
			}
		});

		addRemGroup.pack();

		Button save = new Button(root, SWT.PUSH);
		save.setText("Save");
		save.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				try {
					FileWriter newMenu = new FileWriter("helpmenu.conf");
					PrintWriter file = new PrintWriter(newMenu);
					for (String it : items.getItems()) {
						if (it.equals("----")) {
							file.println("----");
						} else {
							String data = (String) items.getData(it);
							if (data != null)
								file.println(it + "     =  " + data);
						}
					}
					file.flush();
					file.close();
				} catch (IOException ioex) {
					ioex.printStackTrace();
				} finally {
					RepDevMain.mainShell.createMenuDefault();
				}
			}
		});

		// Layout grid data — preserved verbatim from the pre-extraction layout.
		docGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 4, 1));
		name.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		location.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		browse.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

		items.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 2));
		upDownGroup.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, true, 1, 2));
		addRemGroup.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, true, 3, 1));
		save.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 4, 1));

		root.pack();
	}
}
