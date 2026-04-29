package com.repdev;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Centralised logging bootstrap. Routes {@code java.util.logging} (and by
 * extension {@code System.Logger}) plus uncaught exceptions to a rotating log
 * file at {@code ~/.repdev/repdev.log}, and tees {@code System.err} into the
 * same file so the dozens of pre-existing {@code printStackTrace()} call sites
 * are recoverable for incident response.
 *
 * <p>This is critical for jpackage app-image builds: those run with no
 * console, so stdout/stderr are normally written to nowhere. Without this
 * bootstrap, when a packaged user reports "RepDev crashed" there is no log
 * file to ask for.</p>
 *
 * <p>{@link #install()} is idempotent and safe to call multiple times. It
 * MUST be called as the very first thing in {@code main()} so that any
 * exception from later startup code is captured.</p>
 */
public final class Logging {

	private static final String LOG_DIR_NAME = ".repdev";
	private static final String LOG_FILE_NAME = "repdev.log";
	private static final int LOG_FILE_BYTES = 1024 * 1024;   // 1 MB per file
	private static final int LOG_FILE_COUNT = 5;             // 5 rolling files

	private static volatile boolean installed;

	private Logging() { }

	/**
	 * Install the file log + uncaught-exception handler + System.err tee.
	 * Best-effort: any failure in the install itself falls back to the
	 * default JVM behaviour rather than crashing startup.
	 */
	public static synchronized void install() {
		if (installed) return;
		installed = true;

		File logDir = new File(System.getProperty("user.home"), LOG_DIR_NAME);
		if (!logDir.isDirectory() && !logDir.mkdirs()) {
			System.err.println("Logging: could not create " + logDir + " — falling back to console only");
			return;
		}
		File logFile = new File(logDir, LOG_FILE_NAME);

		try {
			// Reset so any earlier configuration is cleared, then attach a
			// rolling file handler at the root logger. Pattern uses %g for the
			// generation suffix; the FileHandler appends mode keeps existing
			// content on JVM restart.
			LogManager.getLogManager().reset();
			FileHandler fh = new FileHandler(logFile.getAbsolutePath() + ".%g", LOG_FILE_BYTES, LOG_FILE_COUNT, true);
			fh.setEncoding("UTF-8");
			fh.setFormatter(new SimpleLineFormatter());
			fh.setLevel(Level.ALL);
			Logger root = Logger.getLogger("");
			root.addHandler(fh);
			root.setLevel(Level.INFO);
		} catch (IOException ex) {
			System.err.println("Logging: could not open " + logFile + ": " + ex);
			return;
		}

		// Tee System.err into a SEPARATE file so legacy printStackTrace()
		// output (45+ call sites) is still recoverable from a packaged build.
		// Critical: it must NOT share the FileHandler's path — FileHandler
		// holds a .lck on the log on Windows and uses non-shared writes, so
		// two writers appending to the same file would interleave bytes
		// mid-line and on Windows cause a FileNotFoundException for the
		// second open. Use a sibling file `repdev-err.log` instead.
		File errFile = new File(logDir, "repdev-err.log");
		java.io.FileOutputStream fileStream = null;
		try {
			final PrintStream originalErr = System.err;
			fileStream = new java.io.FileOutputStream(errFile, true);
			final PrintStream tee = new PrintStream(new TeeStream(originalErr, fileStream), true, "UTF-8");
			System.setErr(tee);
			fileStream = null; // ownership transferred to the tee/PrintStream
		} catch (Exception ex) {
			System.err.println("Logging: could not tee System.err: " + ex);
		} finally {
			if (fileStream != null) {
				try { fileStream.close(); } catch (IOException ignored) { }
			}
		}

		// Catch-all for threads that exit via an uncaught exception. Each
		// dump goes to the file via the root logger (and to original stderr
		// via the tee).
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			public void uncaughtException(Thread t, Throwable e) {
				Logger.getLogger("com.repdev").log(Level.SEVERE, "Uncaught exception in thread " + t.getName(), e);
			}
		});

		Logger.getLogger("com.repdev").info("Logging initialised, file=" + logFile);
	}

	/** One log record per line, lightweight format. */
	private static final class SimpleLineFormatter extends Formatter {
		public String format(LogRecord r) {
			StringBuilder sb = new StringBuilder(160);
			sb.append(new java.util.Date(r.getMillis())).append(' ');
			sb.append('[').append(r.getLevel().getName()).append("] ");
			String src = r.getLoggerName();
			if (src != null) sb.append(src).append(": ");
			sb.append(formatMessage(r));
			sb.append(System.lineSeparator());
			Throwable t = r.getThrown();
			if (t != null) {
				java.io.StringWriter sw = new java.io.StringWriter();
				t.printStackTrace(new java.io.PrintWriter(sw));
				sb.append(sw.getBuffer());
			}
			return sb.toString();
		}
	}

	/** Forward each write to two underlying streams; ignore secondary errors. */
	private static final class TeeStream extends OutputStream {
		private final OutputStream a;
		private final OutputStream b;
		TeeStream(OutputStream a, OutputStream b) { this.a = a; this.b = b; }
		public void write(int x) throws IOException {
			a.write(x);
			try { b.write(x); } catch (IOException ignored) { }
		}
		public void write(byte[] buf, int off, int len) throws IOException {
			a.write(buf, off, len);
			try { b.write(buf, off, len); } catch (IOException ignored) { }
		}
		public void flush() throws IOException {
			a.flush();
			try { b.flush(); } catch (IOException ignored) { }
		}
		public void close() throws IOException {
			try { b.flush(); } catch (IOException ignored) { }
			// Don't close `a` — it's the original System.err.
		}
	}
}
