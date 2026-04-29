/**
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 */

package com.repdev.theme;

import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Scrollable;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;

/**
 * Utility for applying the active theme to a whole dialog/shell at once and
 * keeping it in sync as themes change at runtime.
 *
 * Usage:
 *   ShellTheme.install(myShell);   // once, after the shell's children are built
 *
 * Per-widget notes:
 *   - StyledText is skipped — those are managed by SyntaxHighlighter / EditorComposite.
 *   - Button is skipped — applying colors to native buttons on Windows looks worse
 *     than leaving them with the OS's default gray.
 *   - Everything else (Composite, Group, Label, Text, Combo, List, Table, Tree,
 *     CTabFolder, ToolBar, Sash) gets the theme's editor fg/bg.
 */
public final class ShellTheme {

	private static final String FOCUS_CUE_DATA = "repdev.theme.focusCue";
	private static final String DENSITY_APPLIED_DATA = "repdev.theme.densityApplied";

	private ShellTheme() { }

	/** Apply the current theme to a single shell and install a listener that
	 *  reapplies on every theme change. The listener auto-removes on shell
	 *  disposal. */
	public static void install(final Shell shell) {
		if (shell == null || shell.isDisposed()) return;

		apply(shell, ThemeService.getInstance().getCurrent());

		final ThemeChangedListener listener = r -> {
			if (shell.isDisposed()) return;
			apply(shell, r);
			shell.redraw();
		};
		ThemeService.getInstance().addListener(listener);
		shell.addDisposeListener(e -> ThemeService.getInstance().removeListener(listener));
	}

	/** Recursively apply theme colors to a Control subtree.
	 *
	 *  Uses the new optional tokens (`<surface>`, `<button>`, `<selection>`)
	 *  when the XML defines them, falling back to editor colors otherwise so
	 *  existing themes keep working with no change. */
	public static void apply(Control c, ThemeResources r) {
		if (c == null || c.isDisposed() || r == null) return;

		// StyledText is managed by SyntaxHighlighter / EditorComposite listeners.
		if (c instanceof StyledText) return;

		Color bg = (r.surfaceBackground != null) ? r.surfaceBackground : r.editorBackground;
		Color fg = (r.surfaceForeground != null) ? r.surfaceForeground : r.editorForeground;

		try {
			if (r.uiFont != null) c.setFont(r.uiFont);
		} catch (Exception ignored) { }

		applyDialogDensity(c);
		installFocusCue(c, r);

		if (c instanceof Button) {
			// Per-theme opt-in: only color the Button if the theme explicitly
			// defines button tokens; otherwise reset to OS default. Passing null
			// clears the prior theme's color so we don't retain a dangling ref
			// after its ThemeResources disposes.
			try {
				c.setBackground(r.buttonBackground);  // may be null -> OS default
				c.setForeground(r.buttonForeground);
			} catch (Exception ignored) { }
			return;
		}

		try {
			c.setBackground(bg);
			c.setForeground(fg);
		} catch (Exception ignored) { }

		// Item 4: Sash colors
		if (c instanceof org.eclipse.swt.widgets.Sash || c instanceof org.eclipse.swt.custom.SashForm) {
			if (r.sashColor != null) {
				try { c.setBackground(r.sashColor); } catch (Exception ignored) { }
			}
		}

		// Selection colors. CRITICAL: always set (even to null) so the widget
		// releases its reference to the previous theme's Color, which is about
		// to be disposed. Skipping this block when tokens are absent caused a
		// crash in CTabFolderRenderer.drawBody when switching from a themed
		// theme to an unthemed one.
		// Table/Tree column headers — native SWT draws these above the row area
		// and needs a separate call to color them. Requires SWT 3.106+, which we
		// ship (Maven Central 3.133 = Eclipse 2026-03 / platform 4.39).
		if (c instanceof Table t) {
			try {
				t.setHeaderBackground(bg);
				t.setHeaderForeground(fg);
			} catch (Exception ignored) { }
		} else if (c instanceof Tree t) {
			try {
				t.setHeaderBackground(bg);
				t.setHeaderForeground(fg);
			} catch (Exception ignored) { }
		}

		if (c instanceof CTabFolder f) {
			// Item 2: Inactive tab colors
			Color inactiveBg = (r.tabInactiveBackground != null) ? r.tabInactiveBackground : bg;
			Color inactiveFg = (r.tabInactiveForeground != null) ? r.tabInactiveForeground : fg;
			Color selBg = (r.tabSelectionBackground != null) ? r.tabSelectionBackground : r.selectionBackground;
			Color selFg = (r.tabSelectionForeground != null) ? r.tabSelectionForeground : r.selectionForeground;
			try {
				// Clear both solid and gradient state first. The solid-color setters
				// do not clear CTabFolder's cached gradient arrays, so without the
				// explicit gradient reset the renderer can keep painting with old,
				// soon-to-be-disposed Color handles after theme/zoom changes.
				f.setBackground((Color[]) null, null, false);
				f.setSelectionBackground((Color[]) null, null, false);
				f.setBackground((Color) null);
				f.setForeground((Color) null);
				f.setSelectionForeground(null);
				f.setBackground(inactiveBg);
				f.setForeground(inactiveFg);
				if (r.borderColor != null) f.setBorderVisible(true);
				for (CTabItem item : f.getItems()) {
					item.setForeground(null);
					item.setSelectionForeground(null);
					item.setForeground(inactiveFg);
					if (r.uiFont != null) item.setFont(r.uiFont);
				}
			} catch (Exception ignored) { }

			try {
				f.setSelectionBackground(selBg);
				f.setSelectionForeground(selFg);
				// VS Code-style accent bar under the selected tab.
				if (r.tabSelectionBarThickness >= 0) {
					f.setHighlightEnabled(true);
					f.setSelectionBarThickness(r.tabSelectionBarThickness);
				}
			} catch (Exception ignored) { }
		}

		if (c instanceof Composite cmp) {
			for (Control child : cmp.getChildren()) {
				apply(child, r);
			}
		}
	}

	/**
	 * P-H2: Density is a one-shot property of the layout container — it doesn't
	 * change with the active theme. Without this gate, every theme switch
	 * re-touched the FormLayout/GridLayout/RowLayout fields on every composite
	 * in the shell tree (and SWT can re-trigger layout passes on those mutations
	 * even when the new value equals the old). The {@code Math.max} floors made
	 * the values themselves idempotent, but the writes still cost work on each
	 * switch. The data-flag short-circuit makes the second-and-later
	 * {@code apply()} calls O(0) for density across the whole shell tree.
	 */
	private static void applyDialogDensity(Control c) {
		Shell shell = c.getShell();
		if (shell == null || shell.getParent() == null || !(c instanceof Composite composite)) return;
		if (Boolean.TRUE.equals(composite.getData(DENSITY_APPLIED_DATA))) return;
		Object layout = composite.getLayout();
		if (layout instanceof FormLayout form) {
			form.marginTop = Math.max(form.marginTop, 10);
			form.marginBottom = Math.max(form.marginBottom, 10);
			form.marginLeft = Math.max(form.marginLeft, 10);
			form.marginRight = Math.max(form.marginRight, 10);
			form.spacing = Math.max(form.spacing, 8);
		} else if (layout instanceof GridLayout grid) {
			grid.marginWidth = Math.max(grid.marginWidth, 10);
			grid.marginHeight = Math.max(grid.marginHeight, 10);
			grid.horizontalSpacing = Math.max(grid.horizontalSpacing, 8);
			grid.verticalSpacing = Math.max(grid.verticalSpacing, 8);
		} else if (layout instanceof RowLayout row) {
			row.marginTop = Math.max(row.marginTop, 10);
			row.marginBottom = Math.max(row.marginBottom, 10);
			row.marginLeft = Math.max(row.marginLeft, 10);
			row.marginRight = Math.max(row.marginRight, 10);
			row.spacing = Math.max(row.spacing, 8);
		}
		composite.setData(DENSITY_APPLIED_DATA, Boolean.TRUE);
	}

	private static void installFocusCue(final Control c, final ThemeResources r) {
		if (!(c instanceof Text || c instanceof Combo || c instanceof Spinner)) return;
		if (Boolean.TRUE.equals(c.getData(FOCUS_CUE_DATA))) return;
		c.setData(FOCUS_CUE_DATA, Boolean.TRUE);
		c.addFocusListener(new FocusAdapter() {
			public void focusGained(FocusEvent e) { c.redraw(); }
			public void focusLost(FocusEvent e) { c.redraw(); }
		});
		// Do NOT capture `r` in the closure: ThemeResources gets disposed two
		// ticks after a theme switch, and a stale Color reference here would
		// crash SWT on Windows when the next paint runs. Always look up the
		// live ThemeResources from the service instead, and skip painting if
		// neither token is available rather than falling back to anything
		// captured at install time.
		c.addPaintListener(e -> {
			if (c.isDisposed() || !c.isFocusControl()) return;
			ThemeResources current = ThemeService.getInstance().getCurrent();
			if (current == null) return;
			Color ring = current.selectionBackground != null ? current.selectionBackground : current.borderColor;
			if (ring == null || ring.isDisposed()) return;
			Rectangle area = ((Scrollable) c).getClientArea();
			Color old = e.gc.getForeground();
			e.gc.setForeground(ring);
			e.gc.drawRectangle(area.x, area.y, Math.max(0, area.width - 1), Math.max(0, area.height - 1));
			e.gc.setForeground(old);
		});
	}
}
