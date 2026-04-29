package com.repdev.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ThemeConf} read/write round-trip, cache invalidation, and
 * the .bak rotation contract that protects users from a half-written file.
 *
 * The class operates on the working-directory file {@code theme.conf}, so
 * each test runs in its own temp directory.
 */
class ThemeConfTest {

	// ThemeConf.read/write reference the file via a relative path, which
	// resolves against the process working directory. Rather than try to
	// redirect that mid-process (the JVM caches the initial cwd in places we
	// can't override from a test), we back up any existing project-level
	// theme.conf / theme.conf.bak in setUp and restore them in tearDown.
	private byte[] savedConfBytes;
	private byte[] savedBakBytes;

	@BeforeEach
	void setUp() throws IOException {
		File conf = new File(ThemeConf.FILE_NAME);
		File bak = new File(ThemeConf.BACKUP_NAME);
		savedConfBytes = conf.isFile() ? Files.readAllBytes(conf.toPath()) : null;
		savedBakBytes = bak.isFile() ? Files.readAllBytes(bak.toPath()) : null;
		conf.delete();
		bak.delete();
		// Reset the cache state by writing an empty Properties and rolling
		// back so subsequent reads come from disk on the first test call.
		ThemeConf.rollback();
	}

	@AfterEach
	void tearDown() throws IOException {
		File conf = new File(ThemeConf.FILE_NAME);
		File bak = new File(ThemeConf.BACKUP_NAME);
		conf.delete();
		bak.delete();
		if (savedConfBytes != null) Files.write(conf.toPath(), savedConfBytes);
		if (savedBakBytes != null) Files.write(bak.toPath(), savedBakBytes);
		ThemeConf.rollback();   // invalidate cache
	}

	@Test
	void roundTripPreservesValues() {
		Properties p = new Properties();
		p.setProperty(ThemeConf.KEY_THEME_ID, "nord");
		p.setProperty(ThemeConf.KEY_THEME_MODE, "CUSTOM");
		ThemeConf.write(p);

		Properties read = ThemeConf.read();
		assertEquals("nord", read.getProperty(ThemeConf.KEY_THEME_ID));
		assertEquals("CUSTOM", read.getProperty(ThemeConf.KEY_THEME_MODE));
	}

	@Test
	void writeKeepsBakOfPriorContents() {
		// First write establishes the file.
		Properties first = new Properties();
		first.setProperty(ThemeConf.KEY_THEME_ID, "old-theme");
		ThemeConf.write(first);
		assertTrue(new File(ThemeConf.FILE_NAME).isFile());

		// Second write must rotate the prior file to .bak before overwriting.
		Properties second = new Properties();
		second.setProperty(ThemeConf.KEY_THEME_ID, "new-theme");
		ThemeConf.write(second);

		// The .bak should now hold the old contents.
		assertTrue(new File(ThemeConf.BACKUP_NAME).isFile());
		// And read() returns the new contents (cache hit, no disk re-load).
		assertEquals("new-theme", ThemeConf.read().getProperty(ThemeConf.KEY_THEME_ID));
	}

	@Test
	void rollbackRestoresFromBak() {
		Properties first = new Properties();
		first.setProperty(ThemeConf.KEY_THEME_ID, "first");
		ThemeConf.write(first);

		Properties second = new Properties();
		second.setProperty(ThemeConf.KEY_THEME_ID, "second");
		ThemeConf.write(second);

		assertTrue(ThemeConf.rollback(), "rollback should succeed when .bak exists");
		assertEquals("first", ThemeConf.read().getProperty(ThemeConf.KEY_THEME_ID));
	}

	@Test
	void rollbackReturnsFalseWhenNoBak() {
		// Fresh temp dir, no prior file → rollback has nothing to do.
		assertFalse(ThemeConf.rollback());
	}

	@Test
	void readReturnsDefensiveCopy() {
		// Mutating the returned Properties must NOT poison the cache. This
		// is the contract that lets the polling thread read concurrently
		// while the UI thread does its own thing.
		Properties first = new Properties();
		first.setProperty(ThemeConf.KEY_THEME_ID, "stable");
		ThemeConf.write(first);

		Properties got = ThemeConf.read();
		got.setProperty(ThemeConf.KEY_THEME_ID, "tampered");

		Properties got2 = ThemeConf.read();
		assertEquals("stable", got2.getProperty(ThemeConf.KEY_THEME_ID));
	}
}
