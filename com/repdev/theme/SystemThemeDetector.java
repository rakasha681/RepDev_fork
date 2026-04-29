/**
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 */

package com.repdev.theme;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Detects whether the host OS is currently in dark or light mode.
 *
 * Scope: Windows and GNOME-based Linux (per theme overhaul plan). Other
 * platforms return UNKNOWN and callers should fall back to the user's explicit
 * choice.
 *
 * <p>Windows mechanism: query the registry key
 * {@code HKCU\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize\AppsUseLightTheme}
 * via {@code reg.exe}. A value of {@code 0x0} means dark mode, {@code 0x1}
 * means light.</p>
 *
 * <p>Linux mechanism: {@code gsettings get org.gnome.desktop.interface color-scheme}
 * (GNOME 42+) returns {@code 'prefer-dark'} or {@code 'default'}. Falls back to
 * checking the gtk-theme name for a {@code -dark} suffix.</p>
 *
 * <p>Watching for changes is done by polling on a single-thread
 * {@link ScheduledExecutorService} — simpler and more portable than hooking
 * WM_SETTINGCHANGE or {@code gsettings monitor}. The default 10s interval is a
 * compromise between responsiveness on theme changes and not burning idle CPU
 * spawning {@code reg.exe}/{@code gsettings} thousands of times an hour. All
 * subprocess calls drain stderr (avoids the poller stalling on a full pipe),
 * use {@link Process#waitFor(long, TimeUnit)} with a hard timeout, and
 * {@code destroyForcibly} the child if it exceeds the timeout.</p>
 */
public final class SystemThemeDetector {

	private static final Logger LOG = Logger.getLogger(SystemThemeDetector.class.getName());

	public enum Appearance { LIGHT, DARK, UNKNOWN }

	/** How often to re-poll the OS appearance. Was 2.5s — bumped because
	 * spawning reg.exe / gsettings 34,560×/day was a measurable battery cost
	 * for users who never change their system theme. */
	private static final long POLL_INTERVAL_SECONDS = 10;

	/** Hard cap on how long we wait for {@code reg.exe} / {@code gsettings}
	 * to return. Both should normally complete in a few hundred milliseconds. */
	private static final long PROCESS_TIMEOUT_MS = 1500;

	private SystemThemeDetector() { }

	public static Appearance detect() {
		String os = System.getProperty("os.name", "").toLowerCase();
		if (os.contains("windows")) return detectWindows();
		if (os.contains("linux"))   return detectLinux();
		return Appearance.UNKNOWN;
	}

	private static Appearance detectWindows() {
		String stdout = runSubprocess(
			"reg", "query",
			"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
			"/v", "AppsUseLightTheme"
		);
		if (stdout == null) return Appearance.UNKNOWN;
		Appearance result = Appearance.UNKNOWN;
		for (String line : stdout.split("\\R")) {
			line = line.trim();
			int idx = line.indexOf("REG_DWORD");
			if (idx >= 0) {
				String hex = line.substring(idx + "REG_DWORD".length()).trim();
				try {
					int v = (hex.startsWith("0x") || hex.startsWith("0X"))
							? Integer.parseInt(hex.substring(2), 16)
							: Integer.parseInt(hex);
					result = (v == 0) ? Appearance.DARK : Appearance.LIGHT;
				} catch (NumberFormatException ex) { /* leave UNKNOWN */ }
				break;
			}
		}
		return result;
	}

	private static Appearance detectLinux() {
		// GNOME 42+: color-scheme key
		String colorScheme = runSubprocess(
			"gsettings", "get", "org.gnome.desktop.interface", "color-scheme"
		);
		if (colorScheme != null) {
			String v = colorScheme.trim().toLowerCase().replace("'", "");
			if (v.contains("dark")) return Appearance.DARK;
			if (v.equals("default") || v.contains("light")) return Appearance.LIGHT;
		}

		// Fallback: check gtk-theme name for -dark suffix
		String gtkTheme = runSubprocess(
			"gsettings", "get", "org.gnome.desktop.interface", "gtk-theme"
		);
		if (gtkTheme != null) {
			String v = gtkTheme.trim().toLowerCase();
			if (v.contains("dark")) return Appearance.DARK;
			if (v.length() > 0) return Appearance.LIGHT;
		}

		return Appearance.UNKNOWN;
	}

	/**
	 * Run a short-lived subprocess and return its captured stdout, or null on
	 * any failure. Drains stderr into stdout (so the OS pipe never fills up
	 * and stalls a long-lived poller), enforces a hard timeout, and force-
	 * destroys the child if it overruns. Argv form is used (no shell), so
	 * there's no injection surface even though the args are static.
	 */
	private static String runSubprocess(String... cmd) {
		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.redirectErrorStream(true);
		Process p = null;
		try {
			p = pb.start();
			StringBuilder sb = new StringBuilder();
			try (BufferedReader in = new BufferedReader(
					new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				// Cap captured output so a wedged subprocess that floods
				// stdout cannot keep this thread blocked in readLine until
				// the timeout. 64 KB is far beyond plausible reg.exe /
				// gsettings output (hundreds of bytes) but small enough that
				// even a runaway child doesn't grow the heap unboundedly.
				while (sb.length() < 64 * 1024 && (line = in.readLine()) != null) {
					sb.append(line).append('\n');
				}
				if (sb.length() >= 64 * 1024) {
					// Output exceeded cap — treat as runaway; abort the
					// child so waitFor returns immediately rather than
					// stalling for the full PROCESS_TIMEOUT_MS budget.
					p.destroyForcibly();
					return null;
				}
			}
			if (!p.waitFor(PROCESS_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
				p.destroyForcibly();
				return null;
			}
			return (p.exitValue() == 0) ? sb.toString() : null;
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			if (p != null) p.destroyForcibly();
			return null;
		} catch (IOException ex) {
			return null;
		}
	}

	/**
	 * Start a poller that notifies the given listener whenever the detected
	 * appearance changes. Runs on a dedicated single-thread daemon executor
	 * (one thread, never grows). Returns a runnable that stops the poller —
	 * callers MUST invoke it on shutdown so the executor's worker thread can
	 * terminate cleanly.
	 */
	public static Runnable startPolling(final Listener l) {
		if (l == null) return () -> { };

		final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "repdev-system-theme-poll");
			t.setDaemon(true);
			return t;
		});
		final AtomicReference<Appearance> last = new AtomicReference<>(detect());
		try { l.appearanceChanged(last.get()); }
		catch (Exception ex) { LOG.log(Level.FINE, "System-theme listener threw on initial fire", ex); }

		final ScheduledFuture<?> future = exec.scheduleWithFixedDelay(() -> {
			try {
				Appearance cur = detect();
				Appearance prev = last.get();
				if (cur != prev) {
					last.set(cur);
					try { l.appearanceChanged(cur); }
					catch (Exception ex) { LOG.log(Level.FINE, "System-theme listener threw", ex); }
				}
			} catch (Throwable t) {
				// Swallow throwables: a single failed poll must not stop
				// the schedule, otherwise an early reg.exe/gsettings hiccup
				// would silently kill system-theme tracking forever.
				LOG.log(Level.WARNING, "System theme poll failed", t);
			}
		}, POLL_INTERVAL_SECONDS, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);

		return () -> {
			future.cancel(true);
			exec.shutdownNow();
		};
	}

	public interface Listener {
		void appearanceChanged(Appearance appearance);
	}
}
