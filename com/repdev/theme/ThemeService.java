/**
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 */

package com.repdev.theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

import com.repdev.Config;

/**
 * Central theme registry. Owns the lifecycle of all Color/Font resources
 * belonging to the currently-active theme and fires {@link ThemeChangedListener}
 * on apply.
 *
 * <p>Resource-disposal discipline: consumers MUST re-query on each event and
 * MUST NOT cache the returned resources across events. On apply, the old
 * {@link ThemeResources} snapshot is scheduled for disposal one paint cycle
 * later (via {@link Display#asyncExec}) so in-flight paints can finish using
 * the old handles before they vanish.</p>
 */
public class ThemeService {

	private static final Logger LOG = Logger.getLogger(ThemeService.class.getName());

	private static ThemeService instance;

	public static synchronized ThemeService getInstance() {
		if (instance == null) {
			instance = new ThemeService();
			instance.init();
		}
		return instance;
	}

	// volatile: `current` and `mode` are mutated on the SWT UI thread
	// (applyTheme / setMode) and read from the SystemThemeDetector polling
	// daemon thread. Without volatile the JMM would let the daemon observe
	// a stale value indefinitely. The listener list is never iterated
	// from the daemon, so it stays plain ArrayList — only writes-then-reads
	// across threads need the visibility guarantee.
	private volatile ThemeResources current;
	private volatile ThemeMode mode = ThemeMode.CUSTOM;
	private final List<ThemeChangedListener> listeners = new ArrayList<ThemeChangedListener>();
	private volatile Runnable stopSystemPoller;
	// Retained so shutdown() can remove the global SWT.Show filter that we
	// install on Display.getDefault(). Without this, repeated init/shutdown
	// cycles (e.g. a hosted test harness) would leak a filter per cycle.
	private Listener autoApplyShellFilter;
	private Display autoApplyShellDisplay;

	private ThemeService() { }

	private void init() {
		String themeId = migrateOrReadThemeId();
		current = ThemeResources.load(getDevice(), themeId);
		installShellAutoApplyFilter();
		if (mode == ThemeMode.SYSTEM) startSystemPolling();
	}

	/**
	 * Register a global SWT.Show filter that applies the active theme to every
	 * Shell the first time it is shown. This covers all 20+ dialogs in the app
	 * without requiring each one to explicitly opt in.
	 */
	private void installShellAutoApplyFilter() {
		Display d = Display.getDefault();
		if (d == null || d.isDisposed()) return;
		try {
			Listener f = e -> {
				if (e.widget instanceof Shell) {
					ShellTheme.apply((Shell) e.widget, current);
				}
			};
			d.addFilter(SWT.Show, f);
			autoApplyShellFilter = f;
			autoApplyShellDisplay = d;
		} catch (Exception ex) {
			// Filter registration can fail during shutdown; safe to ignore.
		}
	}

	/**
	 * Resolve the theme id to use at startup.
	 *
	 * <p>Migration: if {@code theme.conf} is absent, write a new one populated
	 * from the legacy {@link Config#getStyle()} field with mode=CUSTOM. The
	 * legacy {@code Config.conf} binary serialization is not touched, so
	 * rolling back to a previous build is safe.</p>
	 */
	private String migrateOrReadThemeId() {
		Properties p = ThemeConf.read();
		String themeId = p.getProperty(ThemeConf.KEY_THEME_ID);
		String modeStr = p.getProperty(ThemeConf.KEY_THEME_MODE);
		if (themeId == null || themeId.length() == 0) {
			themeId = Config.getStyle();
			if (themeId == null || themeId.length() == 0) themeId = "default";
			p.setProperty(ThemeConf.KEY_THEME_ID, themeId);
			p.setProperty(ThemeConf.KEY_THEME_MODE, ThemeMode.CUSTOM.name());
			ThemeConf.write(p);
			System.out.println("ThemeService: migrated theme id '" + themeId + "' to " + ThemeConf.FILE_NAME);
		}

		// Second migration: if the saved themeId points to a file that has been
		// renamed to legacy-<id> (during the theme-pack overhaul) and no modern
		// replacement exists, rewrite the id so Options shows the right name.
		java.io.File cur = new java.io.File("styles" + java.io.File.separator + themeId + ".xml");
		if (!cur.isFile()) {
			java.io.File legacy = new java.io.File("styles" + java.io.File.separator + "legacy-" + themeId + ".xml");
			if (legacy.isFile()) {
				themeId = "legacy-" + themeId;
				p.setProperty(ThemeConf.KEY_THEME_ID, themeId);
				ThemeConf.write(p);
				try { Config.setStyle(themeId); }
				catch (Exception ex) { LOG.log(Level.FINE, "Legacy Config.setStyle failed during legacy-* remap", ex); }
				System.out.println("ThemeService: remapped legacy theme to '" + themeId + "'");
			}
		}

		this.mode = ThemeMode.fromString(modeStr);
		return themeId;
	}

	public ThemeResources getCurrent() { return current; }

	public String getCurrentThemeId() { return (current != null) ? current.themeId : null; }

	public ThemeMode getMode() { return mode; }

	public void setMode(ThemeMode m) {
		if (m == null) return;
		ThemeMode prev = this.mode;
		this.mode = m;
		Properties p = ThemeConf.read();
		p.setProperty(ThemeConf.KEY_THEME_MODE, m.name());
		ThemeConf.write(p);

		if (m == ThemeMode.SYSTEM) {
			startSystemPolling();
		} else if (prev == ThemeMode.SYSTEM) {
			stopSystemPolling();
		}
	}

	public String getSystemLightThemeId() {
		return ThemeConf.read().getProperty(ThemeConf.KEY_SYSTEM_LIGHT, ThemeConf.DEFAULT_SYSTEM_LIGHT);
	}

	public String getSystemDarkThemeId() {
		return ThemeConf.read().getProperty(ThemeConf.KEY_SYSTEM_DARK, ThemeConf.DEFAULT_SYSTEM_DARK);
	}

	public void setSystemLightThemeId(String id) {
		if (id == null) return;
		Properties p = ThemeConf.read();
		p.setProperty(ThemeConf.KEY_SYSTEM_LIGHT, id);
		ThemeConf.write(p);
	}

	public void setSystemDarkThemeId(String id) {
		if (id == null) return;
		Properties p = ThemeConf.read();
		p.setProperty(ThemeConf.KEY_SYSTEM_DARK, id);
		ThemeConf.write(p);
	}

	private void startSystemPolling() {
		stopSystemPolling(); // guard against double-start
		stopSystemPoller = SystemThemeDetector.startPolling(a -> {
			// Dispatch to UI thread; polling runs on a daemon thread.
			Display d = Display.getDefault();
			if (d == null || d.isDisposed()) return;
			d.asyncExec(() -> {
				if (mode != ThemeMode.SYSTEM) return;
				String target;
				if (a == SystemThemeDetector.Appearance.DARK) target = getSystemDarkThemeId();
				else if (a == SystemThemeDetector.Appearance.LIGHT) target = getSystemLightThemeId();
				else return;
				if (target != null && !target.equals(getCurrentThemeId())) {
					applyTheme(target);
				}
			});
		});
	}

	private void stopSystemPolling() {
		if (stopSystemPoller != null) {
			try { stopSystemPoller.run(); }
			catch (Exception ex) { LOG.log(Level.WARNING, "System theme poller shutdown threw", ex); }
			stopSystemPoller = null;
		}
	}

	/**
	 * Load {@code themeId} from {@code styles/}, fire change listeners, and
	 * schedule the previous resource set for disposal on the next paint cycle.
	 *
	 * <p>Safe to call on the UI thread. If called off-UI the listener
	 * notifications will still happen but widgets re-applied from non-UI
	 * threads will fail — callers should route to the UI thread themselves.</p>
	 */
	public void applyTheme(String themeId) {
		if (themeId == null) return;
		ThemeResources old = this.current;
		ThemeResources fresh = ThemeResources.load(getDevice(), themeId);
		this.current = fresh;

		Properties p = ThemeConf.read();
		p.setProperty(ThemeConf.KEY_THEME_ID, themeId);
		if (mode == null) mode = ThemeMode.CUSTOM;
		p.setProperty(ThemeConf.KEY_THEME_MODE, mode.name());
		ThemeConf.write(p);

		// F1: ThemeService is the sole source of truth for the active theme id.
		// We no longer mirror to Config.setStyle — all readers were migrated to
		// ThemeService.getCurrentThemeId(). The migration path in
		// migrateOrReadThemeId() still reads Config.getStyle() once at startup so
		// users coming from pre-1.8.0a builds get their legacy theme preserved.

		fireThemeChanged(fresh);
		scheduleDispose(old);
	}

	public void addListener(ThemeChangedListener l) {
		if (l != null && !listeners.contains(l)) listeners.add(l);
	}

	public void removeListener(ThemeChangedListener l) {
		if (l != null) listeners.remove(l);
	}

	private void fireThemeChanged(ThemeResources r) {
		// Walk every currently-open shell first so dialogs (which don't have
		// their own explicit listener) pick up the new theme. The walker skips
		// widgets that manage their own theming (StyledText, Button).
		Display d = Display.getDefault();
		if (d != null && !d.isDisposed()) {
			for (Shell s : d.getShells()) {
				ShellTheme.apply(s, r);
			}
		}

		// Then fire explicit listeners (EditorComposite, MainShell specifics).
		List<ThemeChangedListener> snapshot = new ArrayList<>(listeners);
		for (ThemeChangedListener l : snapshot) {
			try { l.themeChanged(r); }
			catch (Exception ex) { LOG.log(Level.WARNING, "Theme listener threw", ex); }
		}
	}

	private void scheduleDispose(final ThemeResources old) {
		if (old == null) return;
		Display d = Display.getDefault();
		if (d == null || d.isDisposed()) { old.dispose(); return; }
		d.asyncExec(() -> {
			// Second asyncExec — after listeners have run and any in-flight
			// paint cycles have completed against the old handles.
			Display dd = Display.getDefault();
			if (dd != null && !dd.isDisposed()) {
				dd.asyncExec(old::dispose);
			} else {
				old.dispose();
			}
		});
	}

	private Device getDevice() {
		Display d = Display.getDefault();
		if (d == null) d = Display.getCurrent();
		return d;
	}

	/** Called at app shutdown to release the final active theme. */
	public void shutdown() {
		stopSystemPolling();
		// Remove the global SWT.Show filter installed in init(); otherwise the
		// listener (and the captured `this` chain) outlives the service.
		if (autoApplyShellFilter != null && autoApplyShellDisplay != null
				&& !autoApplyShellDisplay.isDisposed()) {
			try { autoApplyShellDisplay.removeFilter(SWT.Show, autoApplyShellFilter); }
			catch (Exception ex) { LOG.log(Level.FINE, "removeFilter on shutdown failed", ex); }
		}
		autoApplyShellFilter = null;
		autoApplyShellDisplay = null;
		// Drop any ThemeChangedListeners that didn't pair their own remove
		// (e.g. RepDevMain's process-wide Windows-dark-chrome listener).
		// The singleton itself lives till JVM exit, so without this clear
		// the listener and everything it captured (Display, Color refs) stay
		// reachable through the static `instance` field for the rest of the
		// process — minor in single-process apps but visible in repeated
		// init/shutdown harness cycles.
		listeners.clear();
		if (current != null) {
			current.dispose();
			current = null;
		}
	}
}
