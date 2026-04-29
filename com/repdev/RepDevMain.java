/**
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 *  http://repdev.org/ <support@repdev.org>
 *
 *  This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.repdev;

import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageFileNameProvider;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;

/**
 * Main run class, runs as first startup
 * Provides many application global functions/variables, as well as intializes all the stuff we need
 *
 * TODO: Documentation for RepDev
 *
 * @see Awesomeness
 * @author Jake Poznanski, Ryan Schultz, Sean Delaney
 */
public class RepDevMain {
	public static final HashMap<Integer, SymitarSession> SYMITAR_SESSIONS = new HashMap<Integer, SymitarSession>();
	public static HashMap<Integer, SessionInfo> SESSION_INFO = new HashMap<Integer, SessionInfo>();
	public static byte [] MASTER_PASSWORD_HASH;
	public static final boolean DEVELOPER = false; //Set this flag to enable saving passwords, this makes it easy for developers to log in and check stuff quickly after making changes
	public static final int VMAJOR = 1;
	public static final int VMINOR = 7;
	public static final int VFIX   = 8;
	public static final String VSPECIAL = ""; // "special" string for release names, beta, etc

	public static final String VERSION = VMAJOR + "." + VMINOR + (VFIX>0?"."+VFIX:"") + (DEVELOPER ? "-dev" : "") + (!VSPECIAL.equals("")? " " + VSPECIAL : "");
	public static final String NAMESTR = "RepDev v" + VERSION;
	public static boolean FORGET_PASS_ON_EXIT = false; // set in options only please.

	public static MainShell mainShell;
	private static Display display;
	public static Image smallAddImage, smallErrorsImage, smallDataImage, smallFileImage, smallProjectImage, smallRemoveImage, smallRepGenImage, smallSymImage, smallSymOnImage, smallTasksImage, smallActionSaveImage, smallFileAddImage, smallFileRemoveImage,
	smallProjectAddImage, smallProjectRemoveImage, smallRunImage, smallSymAddImage, smallSymRemoveImage, smallDBFieldImage, smallDBRecordImage, smallVariableImage, smallImportImage, smallFileNewImage, smallFileOpenImage, smallDeleteImage,
	smallOptionsImage, smallIndentLessImage, smallIndentMoreImage, smallCutImage, smallCopyImage, smallPasteImage, smallSelectAllImage, smallRedoImage, smallUndoImage, smallFindImage, smallFindReplaceImage, smallExitImage, smallRunFMImage,
	smallWarningImage, smallReportsImage, smallPrintImage, smallFolderImage, smallFolderAddImage, smallFolderRemoveImage, smallActionSaveAsImage, smallProgramIcon, smallInstallImage, smallCompareImage, smallSurroundImage, smallSurroundPrint,
	smallTaskTodo, smallTaskFixme, smallTaskBug, smallTaskWtf, smallTaskTest, smallTaskBookmark, smallTaskNote, smallHighlight, smallHighlightGrey, smallFormatCodeImage, smallInsertSnippetImage, smallFunctionImage, smallSnippetImage, smallKeywordImage, smallDefineVarImage,
	smallRepGenDemandImage;
	public static final String IMAGE_DIR = installRoot() + "repdev-icons/";

	private static String INSTALL_ROOT;

	/**
	 * Absolute directory (with trailing separator) the app was installed into,
	 * i.e. the dir containing the repdev.jar / classes root plus the resource
	 * folders (repdev-icons/, styles/, db.txt, ...). Returns "" when that can't
	 * be determined — callers then fall back to the JVM's working directory,
	 * which is what the source-tree/IDE layout already relies on.
	 *
	 * jpackage installs repdev.jar into &lt;app-image&gt;/app/ alongside those
	 * resource folders, so resolving relative to the jar makes launch work no
	 * matter what CWD the OS chose when the user started the exe.
	 */
	public static String installRoot() {
		if (INSTALL_ROOT != null) return INSTALL_ROOT;
		try {
			java.net.URL u = RepDevMain.class.getProtectionDomain().getCodeSource().getLocation();
			File codeLocation = new File(u.toURI());
			File parent = codeLocation.isFile() ? codeLocation.getParentFile() : codeLocation;
			if (parent != null && new File(parent, "repdev-icons").isDirectory()) {
				INSTALL_ROOT = parent.getAbsolutePath() + File.separator;
				return INSTALL_ROOT;
			}
		} catch (Exception ignored) { }
		INSTALL_ROOT = "";
		return INSTALL_ROOT;
	}

	public static SnippetManager snippetManager;

	private enum CONFIGREV{
		NORMAL, NEW, OUTDATED
	}
	private static CONFIGREV configRev = CONFIGREV.NORMAL;

	public static void main(String[] args) throws Exception {
		// Install file logging + uncaught-exception capture + System.err tee
		// before anything else, so any failure later in startup is recorded.
		// Critical for jpackage app-image builds (no console attached).
		Logging.install();

		display = new Display();

		// Opt into SWT's native dark-mode integration on Windows when the active
		// theme is dark. These are Display.setData keys (not System properties).
		// The native title bar / scrollbars / context menus / tooltips / combos /
		// table selection highlight are all drawn by Windows itself, so enabling
		// the right set here is what actually flips them dark.
		applyWindowsDarkChrome(display);
		// Listener intentionally lives the full process lifetime; ThemeService
		// .shutdown() drops it via its listeners.clear() at app exit.
		com.repdev.theme.ThemeService.getInstance().addListener(r -> applyWindowsDarkChrome(display));

		System.out.println("\nRepDev " + VERSION + " Copyright (C) 2008-2014  RepDev.org Team\n"
				+"This program comes with ABSOLUTELY NO WARRANTY.\n"
				+"This is free software, and you are welcome to redistribute it \n"
				+"under certain conditions.\n");

		System.out.println("Java Runtime version " + System.getProperty("java.runtime.version"));
		System.out.println("---------------------------------------------------------");
		System.out.println("Charset.defaultCharset()                  = " + Charset.defaultCharset());
		System.out.println("System.getProperty(\"file.encoding\")       = " + System.getProperty("file.encoding"));

		try{
			loadSettings();
			com.repdev.theme.ThemeService.getInstance().applyTheme(com.repdev.theme.ThemeService.getInstance().getCurrentThemeId());
			createImages();
			createGUI();

			if (!Config.getPasswordValidator().contentEquals("") && RepDevMain.MASTER_PASSWORD_HASH == null) RepDev_SSO.login(mainShell.shell);
			
			while (!mainShell.isDisposed()) {
				if (!display.readAndDispatch())
					display.sleep();
			}
		} catch(Exception e){
			if( e != null && e.getMessage() != null && e.getMessage().indexOf("[GDI+ is required]") != -1) {
				System.out.println("RepDev Requires GDI+ to be installed in order to run");
				System.out.println("GDI+ can be obtained from: http://www.microsoft.com/downloads/details.aspx?FamilyID=6a63ab9c-df12-4d41-933c-be590feaa05a&DisplayLang=en");
			} else {

				if (display.isDisposed())
					display = new Display();

				e.printStackTrace();

				ErrorDialog errorDialog = new ErrorDialog(e);
				errorDialog.open();

				display.dispose();
			}
		}

		// Save off projects
		ProjectManager.saveAllProjects();
		saveSettings();

		//Close all symitar connections
		for( SymitarSession session : SYMITAR_SESSIONS.values() ){
			if( session != null )
				session.disconnect();
		}

		// Release the active theme + remove the global SWT.Show filter before
		// the Display is destroyed. Avoids leaking the filter (and its captured
		// ThemeService reference) across repeated init/shutdown cycles.
		try { com.repdev.theme.ThemeService.getInstance().shutdown(); }
		catch (Exception ex) {
			java.util.logging.Logger.getLogger(RepDevMain.class.getName())
				.log(java.util.logging.Level.WARNING, "ThemeService.shutdown threw", ex);
		}

		display.dispose();
		System.exit(0);
	}

	private static void createImages() {
		smallActionSaveImage = loadActionIcon("small-action-save");
		smallAddImage = loadActionIcon("small-add");
		smallErrorsImage = loadActionIcon("small-errors");
		smallFileAddImage = loadActionIcon("small-file-add");
		smallFileRemoveImage = loadActionIcon("small-file-remove");
		smallFileImage = loadActionIcon("small-file");
		smallDataImage = loadActionIcon("small-data");
		smallProjectAddImage = loadActionIcon("small-project-add");
		smallProjectRemoveImage = loadActionIcon("small-project-remove");
		smallProjectImage = loadActionIcon("small-project");
		smallRemoveImage = loadActionIcon("small-remove");
		smallRepGenImage = loadActionIcon("small-repgen");
		smallRepGenDemandImage = loadActionIcon("small-repgen-demand");
		smallRunImage = loadActionIcon("small-run");
		smallSymImage = loadActionIcon("small-sym");
		smallSymOnImage = loadActionIcon("small-sym-on");
		smallTasksImage = loadActionIcon("small-tasks");
		smallSymAddImage = loadActionIcon("small-sym-add");
		smallSymRemoveImage = loadActionIcon("small-sym-remove");
		smallDBRecordImage = loadActionIcon("small-db-record");
		smallDBFieldImage = loadActionIcon("small-db-field");
		smallVariableImage = loadActionIcon("small-variable");
		smallImportImage = loadActionIcon("small-import");
		smallFileNewImage = loadActionIcon("small-file-new");
		smallFileOpenImage = loadActionIcon("small-file-open");
		smallDeleteImage = loadActionIcon("small-delete");
		smallOptionsImage = loadActionIcon("small-options");
		smallIndentLessImage = loadActionIcon("small-indent-less");
		smallIndentMoreImage = loadActionIcon("small-indent-more");
		smallCutImage = loadActionIcon("small-cut");
		smallCopyImage = loadActionIcon("small-copy");
		smallPasteImage = loadActionIcon("small-paste");
		smallRedoImage = loadActionIcon("small-redo");
		smallUndoImage = loadActionIcon("small-undo");
		smallSelectAllImage = loadActionIcon("small-select-all");
		smallFindImage = loadActionIcon("small-find");
		smallFindReplaceImage = loadActionIcon("small-find-replace");
		smallExitImage = loadActionIcon("small-exit");
		smallRunFMImage = loadActionIcon("small-run-fm");
		smallWarningImage = loadActionIcon("small-warning");
		smallReportsImage = loadActionIcon("small-reports");
		smallPrintImage = loadActionIcon("small-print");
		smallFolderImage = loadActionIcon("small-folder");
		smallFolderAddImage = loadActionIcon("small-folder-add");
		smallFolderRemoveImage = loadActionIcon("small-folder-remove");
		smallActionSaveAsImage = loadActionIcon("small-action-save-as");

		smallProgramIcon = loadBitmapIcon("monkeyIcon16.png");
		smallInstallImage = loadActionIcon("small-install-repgen");
		smallCompareImage = loadActionIcon("small-compare");
		smallSurroundImage = loadActionIcon("small-surround");
		smallSurroundPrint = loadActionIcon("small-surround-print");

		smallHighlight = loadActionIcon("small-highlight");
		smallHighlightGrey = loadActionIcon("small-highlight-grey");

		smallTaskTodo = loadActionIcon("small-task-todo");
		smallTaskFixme = loadActionIcon("small-task-fixme");
		smallTaskBug = loadActionIcon("small-task-bug");
		smallTaskWtf = loadActionIcon("small-task-wtf");
		smallTaskTest = loadActionIcon("small-task-test");
		smallTaskBookmark = loadActionIcon("small-task-bookmark");
		smallTaskNote = loadActionIcon("small-task-note");

		smallFormatCodeImage = loadActionIcon("small-format-code");
		smallInsertSnippetImage = loadActionIcon("small-insert-snippet");

		smallFunctionImage = loadActionIcon("small-function");
		smallKeywordImage = loadActionIcon("small-keyword");
		smallSnippetImage = loadActionIcon("small-snippet");
		smallDefineVarImage = loadActionIcon("small-define-var");
	}

	public static void reloadImages() {
		createImages();
	}

	private static Image loadActionIcon(final String stem) {
		return new Image(display, new ImageFileNameProvider() {
			public String getImagePath(int zoom) {
				String path = actionIconPath(stem);
				return path != null && new File(path).isFile() ? path : null;
			}
		});
	}

	private static String actionIconPath(String stem) {
		String largePath = IMAGE_DIR + stem + "-large.svg";
		if (Config.getLargeIcons() && new File(largePath).isFile()) return largePath;
		String svgPath = IMAGE_DIR + stem + ".svg";
		return new File(svgPath).isFile() ? svgPath : null;
	}

	private static Image loadBitmapIcon(final String name) {
		return new Image(display, new ImageFileNameProvider() {
			public String getImagePath(int zoom) {
				String path = bitmapIconPath(name, zoom);
				return path != null && new File(path).isFile() ? path : null;
			}
		});
	}

	private static String bitmapIconPath(String name, int zoom) {
		if (name.equals("monkeyIcon16.png") && zoom >= 200 && new File(IMAGE_DIR + "icon-32x32.png").isFile()) {
			return IMAGE_DIR + "icon-32x32.png";
		}
		String path = IMAGE_DIR + name;
		return new File(path).isFile() ? path : null;
	}

	/**
	 * Strict allowlist filter for {@code repdev.conf} deserialization. The
	 * file is user-editable, so an attacker who can write it gets pre-UI
	 * arbitrary deserialization without this guard. Allow only the package
	 * roots {@code Config} actually persists (RepDev's own classes plus the
	 * standard collections / primitive wrappers / SWT Point), reject anything
	 * else, and cap stream size, array length, and graph depth.
	 */
	private static ObjectInputFilter buildConfigDeserializationFilter() {
		final java.util.Set<String> allowedPrefixes = new java.util.HashSet<String>();
		allowedPrefixes.add("com.repdev.");
		allowedPrefixes.add("java.lang.");
		allowedPrefixes.add("java.util.");
		allowedPrefixes.add("java.math.");
		allowedPrefixes.add("java.time.");
		allowedPrefixes.add("org.eclipse.swt.graphics.Point");
		allowedPrefixes.add("[L");  // arrays of object refs (filter walks the element class separately)
		allowedPrefixes.add("[B");
		allowedPrefixes.add("[I");
		allowedPrefixes.add("[J");
		allowedPrefixes.add("[Z");
		allowedPrefixes.add("[C");
		allowedPrefixes.add("[D");
		allowedPrefixes.add("[F");
		allowedPrefixes.add("[S");

		return new ObjectInputFilter() {
			public Status checkInput(FilterInfo info) {
				if (info.streamBytes() > 8L * 1024 * 1024) return Status.REJECTED;       // 8 MB stream cap
				if (info.depth() > 64) return Status.REJECTED;                            // graph depth cap
				if (info.references() > 100_000) return Status.REJECTED;                  // back-reference cap
				if (info.arrayLength() > 1_000_000) return Status.REJECTED;               // array length cap

				Class<?> clazz = info.serialClass();
				if (clazz == null) return Status.UNDECIDED;
				while (clazz.isArray()) clazz = clazz.getComponentType();
				if (clazz.isPrimitive()) return Status.ALLOWED;
				String name = clazz.getName();
				for (String p : allowedPrefixes) {
					if (name.startsWith(p) || name.equals(p)) return Status.ALLOWED;
				}
				return Status.REJECTED;
			}
		};
	}

	/**
	 * Loads Config object and settings from a serialized file Also connects to
	 * all syms in the config file
	 *
	 * Also, starts the snippet manager
	 */
	public static void loadSettings() {
		String localFile = "repdev.conf";
		String userFile = System.getProperty("user.home") + System.getProperty("file.separator") + "repdev.conf";
		String loadFile = localFile;

		if( new File(userFile).exists() && !new File(localFile).exists() ){
			System.out.println("The config file is being copied from it's old location in your user folder, to the local Repdev install folder.\nOld Location: " +
								userFile);
			loadFile = userFile;
		}

		try {
			ObjectInputStream in;
			try {
				in = new ObjectInputStream(new FileInputStream(loadFile));
			} catch (IOException primaryEx) {
				// Primary load file is missing/unreadable. Before falling
				// through to the "first launch" branch, try the .bak that
				// saveSettings rotates ahead of every overwrite — recovers
				// from a SIGKILL between rotate and atomic-move.
				java.io.File bak = new java.io.File("repdev.conf.bak");
				if (loadFile.equals("repdev.conf") && bak.isFile()) {
					System.err.println("repdev.conf missing; recovering from repdev.conf.bak");
					in = new ObjectInputStream(new FileInputStream(bak));
				} else {
					throw primaryEx;
				}
			}
			// Close the stream on every exit path. Without this, a
			// readObject()-time IOException (corrupt/empty conf) leaks the
			// FileInputStream — on Windows the file stays locked until GC
			// finalizes it, so the immediate saveSettings() fallback below
			// fails its rotate/replace with sharing violations. Linux doesn't
			// hit this because POSIX allows renaming open files.
			try (ObjectInputStream stream = in) {
				// CWE-502: repdev.conf is user-editable on disk and the file is
				// read before any UI exists, so an attacker who can write the
				// file gets early-startup arbitrary deserialization. Apply a
				// strict allowlist limited to the (Java collections + Config
				// graph) classes we actually serialize. JEP 290 ObjectInputFilter
				// has been part of the JDK since 9. Anything outside the allowlist
				// rejects with a thrown InvalidClassException.
				stream.setObjectInputFilter(buildConfigDeserializationFilter());
				Config configObject = (Config) stream.readObject();
				Config.setConfig(configObject);
			}

			// Pre-rev-7 configs miss new boolean fields (they deserialize to
			// false regardless of the field initializer). Restore the prior
			// behavior so upgraders aren't surprised.
			if (Config.getRevision() < 7) {
				// spacesForTabs was split out of tabSize — tabSize>0 used to
				// imply spaces, so preserve that.
				if (Config.getTabSize() > 0)
					Config.setSpacesForTabs(true);
				// Formatter has always emitted a blank line after END; keep
				// it on for existing users (new users default to on too).
				Config.setBlankLineAfterEnd(true);
				// Rainbow brackets is a net-new feature; opt existing users in
				// (same default new users get) so the feature is discoverable.
				Config.setRainbowBrackets(true);
			}
		} catch (ClassCastException e) {
			System.out.println("FILE OUT OF DATE!");
		} catch (IOException e) {
			//Any odd defaults
			Config.setRevision(-1);
			Config.setRunOptionsQueue(-1);
			Config.setLastPassword(""); // only saved if RepDevMain.DEVELOPER
			Config.setLastUserID("");
			Config.setLastUsername("");
			Config.setTerminateHour(20);
			Config.setTerminateMinute(0);
			Config.setListUnusedVars(true);

			System.out.println("Creating data file for the first time.");
			saveSettings();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		
		SESSION_INFO = Config.getSessionInfo();
		if(SESSION_INFO == null) {
			SESSION_INFO = new HashMap<Integer, SessionInfo>();
		}
		
		SymitarSession session;

		// Start up data
		for (int sym : Config.getSyms()) {

			if( Config.getServer().equalsIgnoreCase("testsession")) //Allows for a testing mode when no symitar server's are available
				session = new TestingSymitarSession();
			else
				session = new DirectSymitarSession();

			if (SESSION_INFO.get(sym) == null) {
				SessionInfo si = new SessionInfo("", "", "", "", "");
				SESSION_INFO.put(sym, si);
			} else {
				session.setServer(SESSION_INFO.get(sym).getServer());
			}
			SYMITAR_SESSIONS.put(sym, session);
		}
		if(Config.getTerminateHour()==0){
			Config.setTerminateHour(20);
			Config.setTerminateMinute(0);
			saveSettings();
		}

		if(Config.getRevision()==-1){
			configRev = CONFIGREV.NEW;
		}
		else if (Config.getRevision()!=Config.REVISION){
			configRev = CONFIGREV.OUTDATED;
		}
	}

	/**
	 * Saves the config object to a file
	 *
	 */

	public static void saveSettings() {
		try {
			// Write the current syms to the Config file
			ArrayList<Integer> newSyms = new ArrayList<Integer>();
			HashMap<Integer, SessionInfo> newSessionInfo = new HashMap<Integer, SessionInfo>();

			for (int sym : SYMITAR_SESSIONS.keySet()) {
				newSyms.add(sym);
				newSessionInfo.put(sym, SESSION_INFO.get(sym));
			}

			Config.setSyms(newSyms);
			Config.setSessionInfo(newSessionInfo);


			//Only save passwords if DEVELOPER FLAG is on
			if( !DEVELOPER || FORGET_PASS_ON_EXIT ){
				Config.setLastPassword("");
				Config.setLastUserID("");
			}

			// Rotate the existing repdev.conf to repdev.conf.bak so an
			// XMLDecoder/Serialization load failure on next startup (or a
			// crash mid-write here) doesn't lose Symitar hosts and SSO state.
			// Mirrors ThemeConf's .bak pattern. Best-effort: if the rotate
			// fails (FS readonly, perms, etc.) we still try the write so the
			// user isn't blocked from saving. Use Files.move with ATOMIC_MOVE
			// so the rename either fully completes or is fully reverted on a
			// crash mid-rotate (avoids the half-empty File.renameTo gap).
			java.nio.file.Path currentPath = java.nio.file.Paths.get("repdev.conf");
			java.nio.file.Path bakPath = java.nio.file.Paths.get("repdev.conf.bak");
			if (java.nio.file.Files.isRegularFile(currentPath)) {
				try {
					java.nio.file.Files.move(currentPath, bakPath,
							java.nio.file.StandardCopyOption.REPLACE_EXISTING,
							java.nio.file.StandardCopyOption.ATOMIC_MOVE);
				} catch (Exception rotateEx) {
					System.err.println("Could not atomically rotate repdev.conf -> repdev.conf.bak: "
							+ rotateEx + "; saving anyway");
				}
			}

			// Write to a tmp file then atomically move it into place. If we
			// crash mid-write, the user is left with the (intact) .bak — the
			// half-written tmp is harmless and overwritten on next save.
			java.nio.file.Path tmpPath = java.nio.file.Paths.get("repdev.conf.tmp");
			try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(tmpPath.toFile()))) {
				out.writeObject(Config.getConfig());
			}
			java.nio.file.Files.move(tmpPath, currentPath,
					java.nio.file.StandardCopyOption.REPLACE_EXISTING,
					java.nio.file.StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception e) {
			System.err.println("Error saving Config data");
			e.printStackTrace();
			// Best-effort restore from backup so the user doesn't end up with
			// a half-written / zero-byte repdev.conf after a crash mid-write.
			java.nio.file.Path currentPath = java.nio.file.Paths.get("repdev.conf");
			java.nio.file.Path bakPath = java.nio.file.Paths.get("repdev.conf.bak");
			java.nio.file.Path tmpPath = java.nio.file.Paths.get("repdev.conf.tmp");
			try { java.nio.file.Files.deleteIfExists(tmpPath); } catch (Exception ignored) { }
			if (java.nio.file.Files.isRegularFile(bakPath)) {
				try {
					java.nio.file.Files.move(bakPath, currentPath,
							java.nio.file.StandardCopyOption.REPLACE_EXISTING,
							java.nio.file.StandardCopyOption.ATOMIC_MOVE);
				} catch (Exception restoreEx) {
					System.err.println("Could not restore repdev.conf from .bak: " + restoreEx
							+ "; user may need to recover manually from repdev.conf.bak");
				}
			}
		}
	}

	/**
	 * Enable or disable SWT's native Windows dark chrome (title bar, scrollbars,
	 * context menus, tooltips, combos, table-selection highlight) based on the
	 * currently-active theme's luminance. No effect on Linux/GTK.
	 *
	 * These keys are SWT 3.121+ Display.setData properties — they must be set
	 * on the Display instance, NOT via System.setProperty. Setting them again
	 * at runtime flips the native chrome for newly-drawn windows; already-open
	 * windows may need a redraw or (for the title bar) a hide/show cycle to
	 * pick up the change.
	 */
	private static void applyWindowsDarkChrome(Display d) {
		if (d == null || d.isDisposed()) return;
		String os = System.getProperty("os.name", "").toLowerCase();
		if (os.indexOf("windows") == -1) return;

		com.repdev.theme.ThemeResources r = com.repdev.theme.ThemeService.getInstance().getCurrent();
		boolean dark = isDarkTheme(r);
		try {
			Boolean enabled = Boolean.valueOf(dark);
			d.setData("org.eclipse.swt.internal.win32.useDarkModeExplorerTheme", enabled);
			d.setData("org.eclipse.swt.internal.win32.useShellTitleColoring",    enabled);
			d.setData("org.eclipse.swt.internal.win32.Combo.useDarkTheme",       enabled);
			d.setData("org.eclipse.swt.internal.win32.Text.useDarkThemeIcons",   enabled);
			setDisplayColorData(d, "org.eclipse.swt.internal.win32.menuBarForegroundColor", dark, 0xD0, 0xD0, 0xD0);
			setDisplayColorData(d, "org.eclipse.swt.internal.win32.menuBarBackgroundColor", dark, 0x2D, 0x2D, 0x30);
			setDisplayColorData(d, "org.eclipse.swt.internal.win32.menuBarBorderColor",     dark, 0x3E, 0x3E, 0x42);
		} catch (Exception ignored) { }
	}

	private static void setDisplayColorData(Display d, String key, boolean enabled, int red, int green, int blue) {
		if (!enabled) {
			d.setData(key, null);
			return;
		}
		Color color = new Color(d, red, green, blue);
		try {
			d.setData(key, color);
		} finally {
			if (!color.isDisposed()) color.dispose();
		}
	}

	private static boolean isDarkTheme(com.repdev.theme.ThemeResources r) {
		if (r == null || r.editorBackground == null) return false;
		int rr = r.editorBackground.getRed();
		int gg = r.editorBackground.getGreen();
		int bb = r.editorBackground.getBlue();
		// Perceived luminance (Rec. 601): dark if <128.
		return (rr * 299 + gg * 587 + bb * 114) / 1000 < 128;
	}

	private static void createGUI() {
		// Set Default Size
		if(configRev == CONFIGREV.NEW){
			Config.setSashHSize(150);
			Config.setSashVSize(150);
		}

		mainShell = new MainShell(display);
		mainShell.open();
		createGlobalHotkeys();
		if(configRev != CONFIGREV.NORMAL){
			MessageBox msg = new MessageBox(mainShell.getShell(), SWT.ICON_WARNING);
			msg.setText("RepDev Options");

			if(configRev == CONFIGREV.NEW){
				msg.setMessage("Welcome to RepDev. Please take a few moments to configure your Options.");
			}
			else{
				msg.setMessage("The RepDev Team has added new options.  Please take a few moments to configure them.");
			}
			msg.open();
			OptionsShell.show(mainShell.getShell());
			Config.setRevision(Config.REVISION);
		}
	}

	private static void createGlobalHotkeys(){
		Display.getDefault().addFilter(SWT.KeyDown, new Listener() {
			public void handleEvent(Event e) {
				if( e.stateMask == (SWT.CTRL | SWT.SHIFT) ){
//					if(e.keyCode == SWT.F11)
//						RepDevMain.mainShell.toggleFullScreen();
					switch(e.keyCode) {
					case 'f':
					case 'F':
						RepDevMain.mainShell.toggleFullScreen();
						break;
					case 's':
					case 'S':
						RepDevMain.mainShell.saveAllRepgens();
						break;

					case 'o':
					case 'O':
						RepDevMain.mainShell.showOptions();
						break;
					}

				}
				else if (e.stateMask == SWT.CTRL) {
					switch (e.keyCode) {
					case 'o':
					case 'O':
						RepDevMain.mainShell.showFileOpenMenu();
						break;
					}
				}
			}
			});
	}
}
