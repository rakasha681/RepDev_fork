/**
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 */

package com.repdev.options;

import java.io.File;
import java.lang.IllegalArgumentException;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.ColorDialog;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;

import com.repdev.Config;
import com.repdev.RepDevMain;
import com.repdev.theme.ThemeMode;
import com.repdev.theme.ThemeService;

/**
 * Editor tab of the Options dialog. The largest tab, owning formatting
 * controls (tab size, spaces-for-tabs, split-indent-do, blank-line-after-end,
 * rainbow brackets), the theme combo + follow-system checkbox, Live SYM
 * settings, source-control toggle + path, title-bar toggles, line-number /
 * line-guide controls (with live-apply listeners), the hard-line-guide color
 * picker, large-icons toggle, and the no-error-check prefix/suffix.
 *
 * <p>F2 step 5: final per-tab extraction. Closes out the OptionsShell god-class
 * split. Several controls live-apply (theme, line-numbers, line-guides, large
 * icons) — those continue to fire from inside this tab's listeners; Cancel
 * still uses {@link OptionsShellSnapshot#revert()} to roll back, which works
 * because the snapshot reads/writes {@link Config} and {@link ThemeService}
 * directly without needing widget references.</p>
 *
 * <p>{@link #save()} returns {@code false} on hex-validation failure; callers
 * must skip dialog close on a {@code false} return so the user can fix their
 * input.</p>
 */
public final class EditorOptionsTab {

	private final Shell parentShell;
	private final Composite root;

	private final Spinner tabSpinner;
	private final Button spacesForTabsButton;
	private final Button splitIndentDoButton;
	private final Button blankLineAfterEndButton;
	private final Button rainbowBracketsButton;
	private final Combo styleCombo;
	private final Button followSystemTheme;
	private final Text liveSYMText;
	private final Text liveSYMColorText;
	private final Button liveSYMColorSwatch;
	private Color liveSYMColorSwatchColor;
	private final Button useSourceControl;
	private final Text sourceControlDir;
	private final Button varsButton;
	private final Button fileNameInTitle;
	private final Button hostInTitle;
	private final Button viewLineNumbers;
	private final Button showSoftLineGuide;
	private final Button showHardLineGuide;
	private final Spinner softLineGuideColumn;
	private final Spinner hardLineGuideColumn;
	private final Text hardLineGuideColorText;
	private final Button hardLineGuideColorSwatch;
	private Color hardLineGuideColorSwatchColor;
	private final Button largeIcons;
	private final Text errCheckPrefix;
	private final Text errCheckSuffix;

	public EditorOptionsTab(CTabFolder tabs, final Shell parentShell) {
		this.parentShell = parentShell;
		root = new Composite(tabs, SWT.NONE);

		CTabItem tabItem = new CTabItem(tabs, SWT.NONE);
		tabItem.setText("Editor Options");
		tabItem.setControl(root);

		Group editorGroup = new Group(root, SWT.NONE);
		editorGroup.setText("Editor Options");
		FormLayout layout = new FormLayout();
		layout.marginTop = 5;
		layout.marginBottom = 5;
		layout.marginLeft = 5;
		layout.marginRight = 5;
		layout.spacing = 5;
		editorGroup.setLayout(layout);
		root.setLayout(layout);

		Label tabLabel = new Label(editorGroup, SWT.NONE);
		tabLabel.setText("Tab Width (0 for Regular Tabs):");

		tabSpinner = new Spinner(editorGroup, SWT.BORDER);
		tabSpinner.setMaximum(99);
		tabSpinner.setMinimum(0);
		tabSpinner.setSelection(Config.getTabSize());

		Label spacesForTabsLabel = new Label(editorGroup, SWT.NONE);
		spacesForTabsLabel.setText("Insert spaces instead of tabs");

		spacesForTabsButton = new Button(editorGroup, SWT.CHECK);
		spacesForTabsButton.setSelection(Config.getSpacesForTabs());

		Label splitIndentDoLabel = new Label(editorGroup, SWT.NONE);
		splitIndentDoLabel.setText("Split-indent DO/END blocks (format code)");

		splitIndentDoButton = new Button(editorGroup, SWT.CHECK);
		splitIndentDoButton.setSelection(Config.getSplitIndentDoBlocks());

		Label blankLineAfterEndLabel = new Label(editorGroup, SWT.NONE);
		blankLineAfterEndLabel.setText("Blank line after END (format code)");

		blankLineAfterEndButton = new Button(editorGroup, SWT.CHECK);
		blankLineAfterEndButton.setSelection(Config.getBlankLineAfterEnd());

		Label rainbowBracketsLabel = new Label(editorGroup, SWT.NONE);
		rainbowBracketsLabel.setText("Rainbow brackets (colorize (), [], DO/END)");

		rainbowBracketsButton = new Button(editorGroup, SWT.CHECK);
		rainbowBracketsButton.setSelection(Config.getRainbowBrackets());

		Label styleLabel = new Label(editorGroup, SWT.NONE);
		styleLabel.setText("Style");

		styleCombo = new Combo(editorGroup, SWT.DROP_DOWN | SWT.READ_ONLY);
		File dir = new File(RepDevMain.installRoot() + "styles");
		if (dir.isDirectory()) {
			for (String file : dir.list()) {
				if (file.endsWith(".xml")) styleCombo.add(file.substring(0, file.length() - 4));
			}
		}
		String activeThemeId = ThemeService.getInstance().getCurrentThemeId();
		if (activeThemeId != null) styleCombo.setText(activeThemeId);

		// Live-apply: changing the dropdown selection applies the theme immediately.
		// If the user doesn't like it, they pick a different one — no explicit revert.
		styleCombo.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				int idx = styleCombo.getSelectionIndex();
				if (idx < 0) return;
				String chosen = styleCombo.getItem(idx);
				ThemeService.getInstance().applyTheme(chosen);
			}
		});

		Label followSystemLabel = new Label(editorGroup, SWT.NONE);
		followSystemLabel.setText("Follow system theme");
		followSystemTheme = new Button(editorGroup, SWT.CHECK);
		followSystemTheme.setSelection(ThemeService.getInstance().getMode() == ThemeMode.SYSTEM);
		followSystemTheme.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				ThemeMode m = followSystemTheme.getSelection() ? ThemeMode.SYSTEM : ThemeMode.CUSTOM;
				ThemeService.getInstance().setMode(m);
			}
		});

		Label liveSYMLabel = new Label(editorGroup, SWT.NONE);
		liveSYMLabel.setText("Live SYM Number");

		liveSYMText = new Text(editorGroup, SWT.SINGLE | SWT.BORDER);
		try {
			liveSYMText.setText(Integer.toString(Config.getLiveSym()));
		} catch (IllegalArgumentException e) {
			liveSYMText.setText("1999");
		}

		Label liveSYMColorLabel = new Label(editorGroup, SWT.NONE);
		liveSYMColorLabel.setText("Live SYM background Color");

		liveSYMColorText = new Text(editorGroup, SWT.SINGLE | SWT.BORDER);
		try {
			liveSYMColorText.setText(Config.getLiveSymColor());
		} catch (IllegalArgumentException e) {
			liveSYMColorText.setText("FFD7E4");
			liveSYMText.setText("1999");
		}

		liveSYMColorSwatch = new Button(editorGroup, SWT.PUSH);
		applySwatchColor(liveSYMColorText.getText());
		liveSYMColorSwatch.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				ColorDialog dlg = new ColorDialog(parentShell);
				RGB start = parseHex(liveSYMColorText.getText());
				if (start != null) dlg.setRGB(start);
				dlg.setText("Live SYM background color");
				RGB chosen = dlg.open();
				if (chosen != null) liveSYMColorText.setText(rgbToHex(chosen));
			}
		});
		liveSYMColorText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				applySwatchColor(liveSYMColorText.getText());
			}
		});
		liveSYMColorSwatch.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				if (liveSYMColorSwatchColor != null && !liveSYMColorSwatchColor.isDisposed()) {
					liveSYMColorSwatchColor.dispose();
					liveSYMColorSwatchColor = null;
				}
			}
		});

		Label useSourceControlLabel = new Label(editorGroup, SWT.NONE);
		useSourceControlLabel.setText("Use Source Control");

		useSourceControl = new Button(editorGroup, SWT.CHECK);
		useSourceControl.setSelection(Config.getUseSourceControl());

		Label sourceControlDirLabel = new Label(editorGroup, SWT.NONE);
		sourceControlDirLabel.setText("Repository Dir");

		sourceControlDir = new Text(editorGroup, SWT.SINGLE | SWT.BORDER);
		try {
			sourceControlDir.setText(Config.getSourceControlDir());
		} catch (IllegalArgumentException e) {
			sourceControlDir.setText("");
		}

		Label varsLabel = new Label(editorGroup, SWT.NONE);
		varsLabel.setText("List unused variables");

		varsButton = new Button(editorGroup, SWT.CHECK);
		varsButton.setSelection(Config.getListUnusedVars());

		Label nameInTitleLabel = new Label(editorGroup, SWT.NONE);
		nameInTitleLabel.setText("Display file name in main title");

		fileNameInTitle = new Button(editorGroup, SWT.CHECK);
		fileNameInTitle.setSelection(Config.getFileNameInTitle());

		Label hostInTitleLabel = new Label(editorGroup, SWT.NONE);
		hostInTitleLabel.setText("Display host name in main title");
		hostInTitle = new Button(editorGroup, SWT.CHECK);
		hostInTitle.setSelection(Config.getHostNameInTitle());

		Label viewLineNumbersLabel = new Label(editorGroup, SWT.NONE);
		viewLineNumbersLabel.setText("Display line numbers");
		viewLineNumbers = new Button(editorGroup, SWT.CHECK);
		viewLineNumbers.setSelection(Config.getViewLineNumbers());
		viewLineNumbers.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				Config.setViewLineNumbers(viewLineNumbers.getSelection());
				if (RepDevMain.mainShell != null) RepDevMain.mainShell.refreshAllGutters();
			}
		});

		Label showSoftLineGuideLabel = new Label(editorGroup, SWT.NONE);
		showSoftLineGuideLabel.setText("Show soft line guide");
		showSoftLineGuide = new Button(editorGroup, SWT.CHECK);
		showSoftLineGuide.setSelection(Config.getShowSoftLineGuide());
		showSoftLineGuide.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				Config.setShowSoftLineGuide(showSoftLineGuide.getSelection());
				if (RepDevMain.mainShell != null) RepDevMain.mainShell.refreshAllGutters();
			}
		});

		Label showHardLineGuideLabel = new Label(editorGroup, SWT.NONE);
		showHardLineGuideLabel.setText("Show hard line guide");
		showHardLineGuide = new Button(editorGroup, SWT.CHECK);
		showHardLineGuide.setSelection(Config.getShowHardLineGuide());
		showHardLineGuide.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				Config.setShowHardLineGuide(showHardLineGuide.getSelection());
				if (RepDevMain.mainShell != null) RepDevMain.mainShell.refreshAllGutters();
			}
		});

		Label softLineGuideColumnLabel = new Label(editorGroup, SWT.NONE);
		softLineGuideColumnLabel.setText("Soft line guide column");
		softLineGuideColumn = new Spinner(editorGroup, SWT.BORDER);
		softLineGuideColumn.setMinimum(40);
		softLineGuideColumn.setMaximum(240);
		softLineGuideColumn.setSelection(Config.getSoftLineGuideColumn());
		softLineGuideColumn.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				Config.setSoftLineGuideColumn(softLineGuideColumn.getSelection());
				if (RepDevMain.mainShell != null) RepDevMain.mainShell.refreshAllGutters();
			}
		});

		Label hardLineGuideColumnLabel = new Label(editorGroup, SWT.NONE);
		hardLineGuideColumnLabel.setText("Hard line guide column");
		hardLineGuideColumn = new Spinner(editorGroup, SWT.BORDER);
		hardLineGuideColumn.setMinimum(40);
		hardLineGuideColumn.setMaximum(240);
		hardLineGuideColumn.setSelection(Config.getHardLineGuideColumn());
		hardLineGuideColumn.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				Config.setHardLineGuideColumn(hardLineGuideColumn.getSelection());
				if (RepDevMain.mainShell != null) RepDevMain.mainShell.refreshAllGutters();
			}
		});

		Label hardLineGuideColorLabel = new Label(editorGroup, SWT.NONE);
		hardLineGuideColorLabel.setText("Hard line guide color");
		hardLineGuideColorText = new Text(editorGroup, SWT.SINGLE | SWT.BORDER);
		hardLineGuideColorText.setText(Config.getHardLineGuideColor());
		hardLineGuideColorSwatch = new Button(editorGroup, SWT.PUSH);
		applyLineGuideSwatchColor(hardLineGuideColorText.getText());
		hardLineGuideColorSwatch.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				ColorDialog dlg = new ColorDialog(parentShell);
				RGB start = parseHex(hardLineGuideColorText.getText());
				if (start != null) dlg.setRGB(start);
				dlg.setText("Hard line guide color");
				RGB chosen = dlg.open();
				if (chosen != null) hardLineGuideColorText.setText(rgbToHex(chosen));
			}
		});
		hardLineGuideColorText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				applyLineGuideSwatchColor(hardLineGuideColorText.getText());
				RGB rgb = parseHex(hardLineGuideColorText.getText());
				if (rgb == null) return;
				Config.setHardLineGuideColor(hardLineGuideColorText.getText());
				if (RepDevMain.mainShell != null) RepDevMain.mainShell.refreshAllGutters();
			}
		});
		hardLineGuideColorSwatch.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				if (hardLineGuideColorSwatchColor != null && !hardLineGuideColorSwatchColor.isDisposed()) {
					hardLineGuideColorSwatchColor.dispose();
					hardLineGuideColorSwatchColor = null;
				}
			}
		});

		Label largeIconsLabel = new Label(editorGroup, SWT.NONE);
		largeIconsLabel.setText("Large toolbar icons");
		largeIcons = new Button(editorGroup, SWT.CHECK);
		largeIcons.setSelection(Config.getLargeIcons());
		largeIcons.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				Config.setLargeIcons(largeIcons.getSelection());
				if (RepDevMain.mainShell != null) RepDevMain.mainShell.refreshIconPresentation();
			}
		});

		Group noErrorCheckGroup = new Group(root, SWT.NONE);
		noErrorCheckGroup.setText("No Error Check for these Files");
		FormLayout noErrLayout = new FormLayout();
		noErrLayout.marginTop = 5;
		noErrLayout.marginBottom = 5;
		noErrLayout.marginLeft = 5;
		noErrLayout.marginRight = 5;
		noErrLayout.spacing = 5;
		noErrorCheckGroup.setLayout(noErrLayout);

		Label errChkPrefixLabel = new Label(noErrorCheckGroup, SWT.NONE);
		Label errChkSuffixLabel = new Label(noErrorCheckGroup, SWT.NONE);
		errCheckPrefix = new Text(noErrorCheckGroup, SWT.SINGLE | SWT.BORDER);
		errCheckSuffix = new Text(noErrorCheckGroup, SWT.SINGLE | SWT.BORDER);
		errChkPrefixLabel.setText("Prefix");
		errChkSuffixLabel.setText("Suffix");
		// Defensive read for old configs missing these keys — preserves prior
		// catch-and-reset-defaults behaviour.
		try {
			errCheckPrefix.setText(Config.getNoErrorCheckPrefix());
			errCheckSuffix.setText(Config.getNoErrorCheckSuffix());
		} catch (IllegalArgumentException e) {
			Config.setNoErrorCheckPrefix("INC.");
			Config.setNoErrorCheckSuffix(".DEF,.SET,.PRO,.INC");
			errCheckPrefix.setText("INC.");
			errCheckSuffix.setText(".DEF,.SET,.PRO,.INC");
			Config.setFileNameInTitle(true);
			Config.setHostNameInTitle(true);
			fileNameInTitle.setSelection(true);
			hostInTitle.setSelection(true);
			viewLineNumbers.setSelection(true);
			RepDevMain.saveSettings();
		}

		// Layout grid data — preserved verbatim from the pre-extraction layout.
		FormData data = new FormData();
		data.left = new FormAttachment(0);
		data.right = new FormAttachment(100);
		data.top = new FormAttachment(0);
		editorGroup.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(0);
		data.width = 163;
		tabLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(tabLabel);
		data.right = new FormAttachment(100);
		data.top = new FormAttachment(0);
		tabSpinner.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(tabSpinner);
		data.width = 163;
		spacesForTabsLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(spacesForTabsLabel);
		data.top = new FormAttachment(tabSpinner);
		data.right = new FormAttachment(100);
		spacesForTabsButton.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(spacesForTabsButton);
		data.width = 240;
		splitIndentDoLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(splitIndentDoLabel);
		data.top = new FormAttachment(spacesForTabsButton);
		data.right = new FormAttachment(100);
		splitIndentDoButton.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(splitIndentDoButton);
		data.width = 240;
		blankLineAfterEndLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(blankLineAfterEndLabel);
		data.top = new FormAttachment(splitIndentDoButton);
		data.right = new FormAttachment(100);
		blankLineAfterEndButton.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(blankLineAfterEndButton);
		data.width = 280;
		rainbowBracketsLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(rainbowBracketsLabel);
		data.top = new FormAttachment(blankLineAfterEndButton);
		data.right = new FormAttachment(100);
		rainbowBracketsButton.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(rainbowBracketsButton);
		data.width = 163;
		styleLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(styleLabel);
		data.top = new FormAttachment(rainbowBracketsButton);
		data.right = new FormAttachment(100);
		styleCombo.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(styleCombo);
		data.width = 163;
		followSystemLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(followSystemLabel);
		data.top = new FormAttachment(styleCombo);
		data.right = new FormAttachment(100);
		followSystemTheme.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(followSystemTheme);
		data.width = 163;
		liveSYMLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(liveSYMLabel);
		data.top = new FormAttachment(followSystemTheme);
		data.right = new FormAttachment(100);
		liveSYMText.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(liveSYMText);
		data.width = 163;
		liveSYMColorLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(liveSYMColorLabel);
		data.top = new FormAttachment(liveSYMText);
		data.right = new FormAttachment(liveSYMColorSwatch);
		liveSYMColorText.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(liveSYMText);
		data.right = new FormAttachment(100);
		data.width = 28;
		liveSYMColorSwatch.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(liveSYMColorText);
		data.width = 163;
		useSourceControlLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(useSourceControlLabel);
		data.top = new FormAttachment(liveSYMColorText);
		data.right = new FormAttachment(100);
		useSourceControl.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(useSourceControl);
		data.width = 163;
		sourceControlDirLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(sourceControlDirLabel);
		data.top = new FormAttachment(useSourceControl);
		data.right = new FormAttachment(100);
		sourceControlDir.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(sourceControlDir);
		data.width = 163;
		varsLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(varsLabel);
		data.top = new FormAttachment(sourceControlDir);
		data.right = new FormAttachment(100);
		varsButton.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(varsButton);
		data.width = 163;
		nameInTitleLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(nameInTitleLabel);
		data.top = new FormAttachment(varsButton);
		data.right = new FormAttachment(100);
		fileNameInTitle.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(fileNameInTitle);
		data.width = 163;
		hostInTitleLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(hostInTitleLabel);
		data.top = new FormAttachment(fileNameInTitle);
		data.right = new FormAttachment(100);
		hostInTitle.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(hostInTitle);
		data.width = 163;
		viewLineNumbersLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(viewLineNumbersLabel);
		data.top = new FormAttachment(hostInTitle);
		data.right = new FormAttachment(100);
		viewLineNumbers.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(viewLineNumbers);
		data.width = 163;
		showSoftLineGuideLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(showSoftLineGuideLabel);
		data.top = new FormAttachment(viewLineNumbers);
		data.right = new FormAttachment(100);
		showSoftLineGuide.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(showSoftLineGuide);
		data.width = 163;
		showHardLineGuideLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(showHardLineGuideLabel);
		data.top = new FormAttachment(showSoftLineGuide);
		data.right = new FormAttachment(100);
		showHardLineGuide.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(showHardLineGuide);
		data.width = 163;
		softLineGuideColumnLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(softLineGuideColumnLabel);
		data.top = new FormAttachment(showHardLineGuide);
		data.right = new FormAttachment(100);
		softLineGuideColumn.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(softLineGuideColumn);
		data.width = 163;
		hardLineGuideColumnLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(hardLineGuideColumnLabel);
		data.top = new FormAttachment(softLineGuideColumn);
		data.right = new FormAttachment(100);
		hardLineGuideColumn.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(hardLineGuideColumn);
		data.width = 163;
		hardLineGuideColorLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(hardLineGuideColorLabel);
		data.top = new FormAttachment(hardLineGuideColumn);
		data.right = new FormAttachment(hardLineGuideColorSwatch);
		hardLineGuideColorText.setLayoutData(data);

		data = new FormData();
		data.top = new FormAttachment(hardLineGuideColumn);
		data.right = new FormAttachment(100);
		data.width = 28;
		hardLineGuideColorSwatch.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(hardLineGuideColorText);
		data.width = 163;
		largeIconsLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(largeIconsLabel);
		data.top = new FormAttachment(hardLineGuideColorText);
		data.right = new FormAttachment(100);
		largeIcons.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.right = new FormAttachment(100);
		data.top = new FormAttachment(editorGroup);
		noErrorCheckGroup.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(0);
		data.width = 40;
		errChkPrefixLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(errChkPrefixLabel);
		data.top = new FormAttachment(0);
		data.right = new FormAttachment(100);
		errCheckPrefix.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(0);
		data.top = new FormAttachment(errChkPrefixLabel, 4);
		data.width = 40;
		errChkSuffixLabel.setLayoutData(data);

		data = new FormData();
		data.left = new FormAttachment(errChkSuffixLabel);
		data.top = new FormAttachment(errChkPrefixLabel, 4);
		data.right = new FormAttachment(100);
		errCheckSuffix.setLayoutData(data);
	}

	/**
	 * Validate hex inputs, then commit all editor-tab fields to {@link Config}.
	 *
	 * @return {@code true} on success; {@code false} if a hex field failed
	 *         validation. On {@code false}, callers must <i>not</i> close the
	 *         dialog so the user can correct the input.
	 */
	public boolean save() {
		if (!validateHex(hardLineGuideColorText.getText(),
				"Incorrect value entered for the hard line guide color. Please specify 6 hexadecimal digits only. Example C8C8C8")) {
			hardLineGuideColorText.setText(Config.DEFAULT_HARD_LINE_GUIDE_COLOR);
			return false;
		}
		if (!validateHex(liveSYMColorText.getText(),
				"Incorrect value entered for the Live SYM background color. Please specify 6 hexadecimal only.  Example FFD7E4")) {
			liveSYMColorText.setText("FFD7E4");
			return false;
		}

		Config.setTabSize(tabSpinner.getSelection());
		Config.setSpacesForTabs(spacesForTabsButton.getSelection());
		Config.setSplitIndentDoBlocks(splitIndentDoButton.getSelection());
		Config.setBlankLineAfterEnd(blankLineAfterEndButton.getSelection());
		Config.setRainbowBrackets(rainbowBracketsButton.getSelection());
		Config.setListUnusedVars(varsButton.getSelection());
		Config.setNoErrorCheckPrefix(errCheckPrefix.getText());
		Config.setNoErrorCheckSuffix(errCheckSuffix.getText());
		Config.setFileNameInTitle(fileNameInTitle.getSelection());
		Config.setHostNameInTitle(hostInTitle.getSelection());
		Config.setViewLineNumbers(viewLineNumbers.getSelection());
		Config.setShowSoftLineGuide(showSoftLineGuide.getSelection());
		Config.setShowHardLineGuide(showHardLineGuide.getSelection());
		Config.setSoftLineGuideColumn(softLineGuideColumn.getSelection());
		Config.setHardLineGuideColumn(hardLineGuideColumn.getSelection());
		Config.setHardLineGuideColor(hardLineGuideColorText.getText());
		Config.setLargeIcons(largeIcons.getSelection());
		Config.setLiveSym(Integer.parseInt(liveSYMText.getText()));
		Config.setLiveSymColor(liveSYMColorText.getText());
		Config.setUseSourceControl(useSourceControl.getSelection());
		Config.setSourceControlDir(sourceControlDir.getText());
		// Theme + follow-system already applied live via the styleCombo / followSystemTheme listeners.
		return true;
	}

	private void applySwatchColor(String hex) {
		if (liveSYMColorSwatch == null || liveSYMColorSwatch.isDisposed()) return;
		RGB rgb = parseHex(hex);
		if (rgb == null) return;
		Color old = liveSYMColorSwatchColor;
		liveSYMColorSwatchColor = new Color(Display.getCurrent(), rgb);
		liveSYMColorSwatch.setBackground(liveSYMColorSwatchColor);
		if (old != null && !old.isDisposed()) old.dispose();
	}

	private void applyLineGuideSwatchColor(String hex) {
		if (hardLineGuideColorSwatch == null || hardLineGuideColorSwatch.isDisposed()) return;
		RGB rgb = parseHex(hex);
		if (rgb == null) return;
		Color old = hardLineGuideColorSwatchColor;
		hardLineGuideColorSwatchColor = new Color(Display.getCurrent(), rgb);
		hardLineGuideColorSwatch.setBackground(hardLineGuideColorSwatchColor);
		if (old != null && !old.isDisposed()) old.dispose();
	}

	private boolean validateHex(String value, String msg) {
		if (Pattern.matches("[a-fA-F0-9]{6}", value)) return true;
		MessageBox dialog = new MessageBox(parentShell, SWT.ICON_ERROR | SWT.OK);
		dialog.setMessage(msg);
		dialog.setText("Input Error");
		dialog.open();
		return false;
	}

	private static RGB parseHex(String hex) {
		if (hex == null) return null;
		String s = hex.trim();
		if (s.startsWith("#")) s = s.substring(1);
		if (s.length() != 6) return null;
		try {
			int r = Integer.parseInt(s.substring(0, 2), 16);
			int g = Integer.parseInt(s.substring(2, 4), 16);
			int b = Integer.parseInt(s.substring(4, 6), 16);
			return new RGB(r, g, b);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static String rgbToHex(RGB rgb) {
		if (rgb == null) return "FFD7E4";
		return String.format("%02X%02X%02X", rgb.red, rgb.green, rgb.blue);
	}
}
