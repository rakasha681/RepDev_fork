/**
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 */

package com.repdev.theme;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Reader/writer for the sidecar {@code theme.conf} file. Stores the theme id
 * and mode separately from the legacy {@code Config.conf} binary serialization
 * so the old file format is untouched and rollback to a previous build is safe
 * — old builds simply ignore {@code theme.conf}.
 *
 * Uses java.util.Properties (built-in, robust) rather than JSON to avoid adding
 * a parser dependency for what is only a handful of keys.
 */
public final class ThemeConf {

	public static final String FILE_NAME = "theme.conf";
	public static final String BACKUP_NAME = "theme.conf.bak";

	public static final String KEY_THEME_ID = "themeId";
	public static final String KEY_THEME_MODE = "themeMode";
	public static final String KEY_LAST_SYS_THEME = "lastKnownSystemTheme";
	public static final String KEY_SYSTEM_LIGHT = "systemLightThemeId";
	public static final String KEY_SYSTEM_DARK = "systemDarkThemeId";

	public static final String DEFAULT_SYSTEM_LIGHT = "default";
	public static final String DEFAULT_SYSTEM_DARK = "dark";

	// Cached, immutable view of the most-recently-read or -written contents.
	// Callers receive a defensive clone so they cannot mutate the cache by
	// accident. Invalidated to null whenever the underlying file is rewritten
	// out-of-band (rollback) and refreshed in write(). volatile so the polling
	// system-theme detector and the UI thread agree on the current state.
	private static volatile Properties cached;
	// Last-known file mtime captured when `cached` was populated. read()
	// stat-checks the file each call: if the mtime advanced (e.g. user edited
	// theme.conf in a text editor while RepDev was running) we drop the cache
	// and reload, honouring the file's documented "externally editable" contract
	// without paying for a full Properties.load() on every getter.
	private static volatile long cachedMtime;

	private ThemeConf() { }

	public static Properties read() {
		File f = new File(FILE_NAME);
		long currentMtime = f.isFile() ? f.lastModified() : 0L;

		Properties hit = cached;
		if (hit != null && currentMtime == cachedMtime) return cloneOf(hit);

		Properties p = new Properties();
		if (!f.isFile()) {
			cached = p;
			cachedMtime = 0L;
			return cloneOf(p);
		}
		try (FileInputStream in = new FileInputStream(f)) {
			p.load(in);
		} catch (IOException ex) {
			System.err.println("Failed to read " + FILE_NAME + ": " + ex.getMessage());
		}
		cached = p;
		cachedMtime = currentMtime;
		return cloneOf(p);
	}

	/**
	 * Write the given properties, keeping a {@code theme.conf.bak} snapshot of
	 * the previous contents so one-click rollback is possible.
	 *
	 * <p>Two-phase atomic write: first stages the new content to {@code
	 * theme.conf.tmp}, then atomically moves the existing {@code theme.conf}
	 * (if any) to {@code theme.conf.bak} and the tmp into place. A crash
	 * between phases leaves either the previous file intact or, in the worst
	 * case, the rotated {@code .bak} — never a half-written {@code theme.conf}.
	 * Mirrors the {@code Config.save} pattern from F3.</p>
	 */
	public static void write(Properties p) {
		Path tmp = Paths.get(FILE_NAME + ".tmp");
		Path cur = Paths.get(FILE_NAME);
		Path bak = Paths.get(BACKUP_NAME);
		try (FileOutputStream out = new FileOutputStream(tmp.toFile())) {
			p.store(out, "RepDev theme settings - edited by ThemeService");
		} catch (IOException ex) {
			System.err.println("Failed to write " + FILE_NAME + ": " + ex.getMessage());
			try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
			return;
		}
		try {
			if (Files.isRegularFile(cur)) {
				Files.move(cur, bak, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			Files.move(tmp, cur, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException ex) {
			System.err.println("Failed to publish " + FILE_NAME + ": " + ex.getMessage());
			try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
			return;
		}
		// Refresh the cache so subsequent reads see the new state without
		// touching disk. Clone so post-write mutations of `p` by the caller
		// don't leak into the cache. Stamp the mtime so the next read()
		// stat-check sees a stable file and stays on the cached path.
		cached = cloneOf(p);
		File written = cur.toFile();
		cachedMtime = written.isFile() ? written.lastModified() : 0L;
	}

	/** Restore theme.conf from its .bak (for the Revert UX). */
	public static boolean rollback() {
		File bak = new File(BACKUP_NAME);
		if (!bak.isFile()) return false;
		File cur = new File(FILE_NAME);
		if (cur.isFile()) cur.delete();
		boolean ok = bak.renameTo(cur);
		cached = null;
		cachedMtime = 0L;
		return ok;
	}

	private static Properties cloneOf(Properties src) {
		Properties copy = new Properties();
		copy.putAll(src);
		return copy;
	}
}
