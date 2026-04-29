# Changelog

All notable changes to RepDev are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and this project
adheres to [Semantic Versioning](https://semver.org/) where it can —
config-format breaks are called out explicitly.

## [2.0.0] — 2026-04-28

A codebase-modernization release. Three large feature areas (theme system,
code folding, editor/UI rework) ship alongside a comprehensive
quality/security/performance pass landed across thirteen autopilot review
runs against the report at `.full-review/05-final-report.md`. All ten P0
items, the full P1 list, and the bulk of the Sprint-3 P2 modernization
sweep are closed; 61/61 unit tests pass across 8 test classes.

This is the first release on the new versioning track. The prior
shipping line was 1.7.x; the 1.8.0a development series was never released
externally and is collapsed into 2.0.0.

### Added

#### Features

- **Theme system** — new `com/repdev/theme/` package with a service-based
  registry, runtime apply across every shell, light/dark/system modes, and
  a bundled XML theme library (Nord, Solarized light & dark, Dracula
  variants, ocean-light, sepia-soft, platinum-light, rose-light,
  monochrome, and more). System mode follows the host OS appearance on
  Windows and GNOME.
- **`theme.conf` sidecar** at the project root holds the active theme id,
  mode (`CUSTOM` / `SYSTEM`), and per-light/per-dark theme choices. Stored
  as a Properties file separate from the binary `repdev.conf` so a
  downgrade can ignore it cleanly.
- **Code folding** — gutter triangles, `Ctrl+-` collapse / `Ctrl+=`
  expand, collapse-all / expand-all, save-time reassembly, and
  orphan-closer recovery for partial bracket folds. Find/Replace can opt
  into searching folded sections via a toggle in the dialog.
- **Folded-section search** — RepGen's parser exposes hidden text via the
  new `HiddenTextProvider` interface; unused-variable warnings now also
  scan the bodies of collapsed regions instead of false-flagging
  variables that are only used inside a fold.
- **Rainbow brackets** for nested expression grouping.

#### Infrastructure

- **JUnit 5 + Maven Surefire** wired into `pom.xml`. Tests live under
  `${project.basedir}/test` (matching the unconventional source root the
  project uses) and are excluded from the production jar. Eight test
  classes ship with the release: `ConfigLineGuideTest` (4),
  `ThemeConfTest` (5), `StyleXxeHardeningTest` (4), `IndenterTest` (8),
  `FoldingManagerComputeRangesTest` (7), `GutterRendererWidthTest` (6),
  `RepgenParserContainsWordTest` (17), `FormatterGoldenTest` (10) —
  61 tests total.
- **`com.repdev.Logging` bootstrap** — routes `java.util.logging` to a
  rotating `~/.repdev/repdev.log` (1 MB × 5), installs a default
  uncaught-exception handler, and tees `System.err` to a separate
  `repdev-err.log` so the dozens of pre-existing `printStackTrace()`
  call sites are recoverable. Critical for jpackage app-image users
  who run with no console.
- **`docs/theme-conf.md`**, **`docs/folding.md`**, **`styles/AGENTS.md`** —
  user-facing reference for the new theme key/value schema, the folding
  feature, and the styles XML schema (`<header>`, `<palette>`,
  `extends=`, `@palette-ref`, plus the new attrs `gutterBg`,
  `selectionBg`, `inactiveBg`, `selThickness`).

### Changed

#### Architecture

- **`ThemeService` is the sole authority for the active theme id.**
  `applyTheme` no longer mirrors to `Config.setStyle`. `OptionsShell` and
  `MainShell.compareFiles` migrated to `ThemeService.getCurrentThemeId()`.
  `Config.getStyle` and `Config.setStyle` are `@Deprecated` and retained
  only for the one-shot legacy migration path in
  `ThemeService.migrateOrReadThemeId()`.
- **`OptionsShell` god-class broken up.** The 1,409-LOC settings dialog
  is now a 117-LOC orchestrator (−1,292 LOC / −92%) over four per-tab
  Composites in the new `com.repdev.options` package:
  `DeveloperOptionsTab`, `DocumentationOptionsTab`, `ServerOptionsTab`,
  `EditorOptionsTab`, plus `OptionsShellSnapshot` — a single immutable
  capture/revert that replaces eight `snapshotXxx` fields and a
  40-line per-field Cancel revert.
- **`EditorComposite` god-class broken up.** Folding, undo, gutter
  painting, and indentation logic extracted into their own classes:
  `EditorFoldHost` (interface, breaks the bidirectional concrete
  coupling between `FoldingManager` and the editor), `UndoController`
  (undo/redo stacks, named-mode state machine, `TextChange`,
  `FOLD_OP_*` constants), `GutterRenderer` (line numbers, line-length
  guides with cached colour resources, gutter-width arithmetic), and
  `Indenter` (tab-string config, multi-line group indent/dedent,
  leading-whitespace helper). Cumulative: 2,617 → 2,171 LOC
  (−446 LOC / −17%).
- **Folding ↔ editor coupling inverted.** `HiddenTextProvider` lets the
  parser query collapsed-region content without depending on the
  concrete `EditorComposite` type, eliminating a long-standing
  parser↔editor cycle.

#### Compatibility

- **`Config.REVISION` 6 → 7.** New boolean fields default to false on
  deserialization; the upgrade path explicitly restores prior behaviour
  — `spacesForTabs` is enabled when `tabSize > 0`, `blankLineAfterEnd`
  and rainbow brackets are turned on for existing users so the new
  features are discoverable.
- **Built-in theme renames.** The legacy `classic`, `zen`, and `ocean`
  themes have been moved to the `legacy-*` namespace. Users running an
  upgrade will see the renamed entry in the theme picker; their
  preference is migrated automatically when first loaded if RepDev
  recognises the old id.
- **Version bumped** to `2.0.0`.

#### Performance

- **lvar lookup is O(1)** via a lazily-built, `volatile`-cached
  `Set<String>` view on `RepgenParser` (invalidated at the two `lvars`
  mutation sites). `SyntaxHighlighter.getStyle` no longer scans the
  full list per token.
- **Folding recompute is debounced** at 50 ms via `Display.timerExec`.
  Bulk pastes coalesce onto one tick instead of N.
- **Gutter redraws are scoped** to the visible client-area height
  (`clientArea.height`) instead of `lineHeight × lineCount`, fixing a
  full-buffer invalidate on every modify.
- **`paintMarkers` is O(1) per line** — a `Map<Integer, FoldRange>`
  index keyed by start line replaces the per-paint linear scan, and
  the `int[6]` allocation is hoisted out of the paint loop.
- **`Style.getValue` is O(1) per lookup** — a per-loaded-style
  `Map<String, String>` replaces the O(N×M) DOM walk that ran ~50
  times per theme load.
- **`ThemeConf` caches its parsed Properties** with mtime-based
  invalidation, so external edits to `theme.conf` are picked up on
  next read without paying for a `Properties.load()` on every getter.
- **System theme detector** runs on a `ScheduledExecutorService` (one
  daemon worker), drains stderr via `redirectErrorStream`, applies a
  1.5 s subprocess timeout, and `destroyForcibly`s hung children. Poll
  interval is now 10 s (was 2.5 s).
- **`ShellTheme.applyDialogDensity` is one-shot per Composite,** gated
  by a `repdev.theme.densityApplied` data flag. Density is a fixed
  property of the layout container, not theme-dependent, so it runs
  once per Composite lifetime instead of on every theme switch.

#### Settings dialog

- **Live-applied colour fields are validated *before* `Config.set...`
  runs.** Line-guide colour and Live SYM colour use a strict
  `[a-fA-F0-9]{6}` regex; invalid hex no longer commits bad state and
  is surfaced as a real input error that keeps the dialog open for
  correction.

### Security

- **CWE-611 (XXE)** — `Style.java` and `Snippet.java` now build their
  `DocumentBuilderFactory` with DOCTYPE / external-entity / XInclude /
  entity-expansion all disabled. Theme XMLs are user-editable, and
  `extends=` recurses, so without this hardening a tampered styles
  file could read arbitrary local files or DoS startup via the
  billion-laughs entity.
- **CWE-502 (untrusted deserialization)** — `repdev.conf`
  deserialization installs an `ObjectInputFilter` allowlist
  (`com.repdev.*`, `java.lang.*`, `java.util.*`, `java.math.*`,
  `java.time.*`, `org.eclipse.swt.graphics.Point`, primitive arrays)
  plus 8 MB stream / 64-deep / 100k-ref / 1M-array caps. The
  `com.repdev.*` prefix is intentionally generous to avoid breaking
  existing user configs on the REVISION 6→7 upgrade; a follow-up
  release can tighten it to an exact class-name allowlist
  (`Config`, `SessionInfo`, …) once telemetry shows what's actually
  serialized.
- **CWE-59 / CWE-367 (atomic config write)** — `Config.save()` and
  `ThemeConf.write()` both stage to `*.tmp` and use
  `Files.move(..., ATOMIC_MOVE)` for the pre-write rotate and the
  final swap. A SIGKILL between any two phases leaves either the
  previous live file or the rotated `.bak` intact — never a
  half-written file. `loadSettings()` falls back to `repdev.conf.bak`
  automatically if the primary file is missing or corrupt.
- **`ThemeService.current` / `mode` are `volatile`** to close the JMM
  visibility race between the SWT UI thread and the system-theme
  polling daemon.
- **Silent `catch (Exception ignored) { }` sites audited.** The
  genuinely-suspicious sites in `ThemeService`, `SystemThemeDetector`,
  and `RepDevMain.shutdown` route through `java.util.logging` (which
  the new `Logging` bootstrap feeds into `~/.repdev/repdev.log`).
  Two clusters are deliberately preserved as best-effort: the XXE
  feature toggles in `Style.java` / `Snippet.java` (cross-JDK
  tolerance is documented) and the `ShellTheme.apply` 8× catches
  (defensive on dispose paths where logging would spam during
  shutdown).

### Fixed

- **Crash class** — `ShellTheme.installFocusCue` no longer captures the
  install-time `ThemeResources` in its paint listener. After a theme
  switch, the captured Color reference would be disposed two ticks
  later by the `asyncExec` dispose schedule and the next focus paint
  could call `gc.setForeground(disposedColor)`, crashing SWT on
  Windows. Listeners now resolve the live `ThemeResources` at paint
  time and skip drawing if no tokens are available.
- **Data-loss class** — `Config.save()` rotates the existing
  `repdev.conf` to `repdev.conf.bak` before writing the new file. A
  failed deserialization (or a crash mid-write) on the next startup
  falls back to the backup instead of losing Symitar host configs and
  SSO state. Best-effort restore on write failure too.
- **First-launch config-save lock on Windows.** When `readObject()`
  threw on a corrupt/empty `repdev.conf`, the `ObjectInputStream` was
  never closed (the `in.close()` after the read was unreachable on the
  exception path), leaving the file locked on Windows; the immediate
  `saveSettings()` fallback then failed with a sharing violation.
  Linux didn't repro (POSIX rename-while-open). Fix: wrap the read in
  `try / finally` so the stream always closes.
- **Line-length guides drew at ~1.5× their configured column on
  DPI-scaled displays** (Windows ≥ 125%, GNOME `text-scaling-factor`
  > 1). Root cause: `EditorComposite` paint listener measured char
  advance via `gc.getAdvanceWidth('0')`, which on GTK uses different
  metrics than the Pango-based `TextLayout` `StyledText` actually
  uses for character positioning. Fix: set the GC font to
  `txt.getFont()`, then measure with `gc.textExtent("0" × 100) / 100`,
  routing through the same Pango path StyledText uses and averaging
  out per-glyph rounding.
- **`FoldingManager.collapseInternal` rolls back on mismatch.** Before,
  a divergence between expected and actual hidden-text removal logged
  and continued, silently corrupting the buffer. Now the operation
  fails fast and re-inserts the captured hidden text.
- **`Style.getColor` `$rand` / `$red` / `$green` / `$blue` are
  deterministic** per `(theme name, item, attrib, tag)` seed.
  Re-renders no longer churn user-visible colours.
- **`ThemeService.installShellAutoApplyFilter` lifetime leak closed.**
  The Display-scoped Shell filter is removed on `shutdown()`, called
  from `RepDevMain` on exit. The `RepDevMain.main` Windows-dark-chrome
  listener is reclaimed by `ThemeService.shutdown()` clearing the
  listener registry — the only formerly-asymmetric listener
  registration in the tree.
- **Validation order in `OptionsShell.Save`** (see *Changed* above).
- **Folding** — bracket-fold orphan closer (`]` left in the buffer at
  `headerLine + 1`) was being matched against an outer head and
  producing bogus foldable ranges; now skipped explicitly.

### Modernized (Java 21)

- **try-with-resources** applied to `ThemeConf.read/write`,
  `SystemThemeDetector.runSubprocess`, and the `ObjectInputStream` /
  `ObjectOutputStream` blocks in `RepDevMain.loadSettings/saveSettings`.
- **`EditorStyle` is now a `record(Color fg, Color bg, int fontStyle)`**
  with a static `EditorStyle.of(device, frgb, bgrgb [, fontStyle])`
  factory; the unused `getForeground/getBackground/getFontStyle`
  getters drop. All 24 construction sites in `ThemeResources` migrated.
- **SAM-interface anonymous classes in `theme/` collapsed to
  lambdas:** `ThemeChangedListener`, `DisposeListener`, `PaintListener`,
  `Listener`, `Runnable`, `ThreadFactory`. (`FocusAdapter` kept as anon
  class because it overrides two methods.)
- **Pattern-matching `instanceof X x`** across all five
  `ShellTheme.apply` / `applyDialogDensity` cast sites (Table, Tree,
  CTabFolder, Composite; FormLayout, GridLayout, RowLayout, Composite),
  plus three enhanced-for conversions over `CTabItem[]` / `Control[]` /
  `Shell[]`.

### Architectural notes for contributors

- **Long-lived SWT listeners must NOT capture `ThemeResources` in their
  closure.** Resolve it via `ThemeService.getInstance().getCurrent()` at
  paint time and skip the draw if it's null. The codebase had one such
  bug (the focus-cue paint listener); guideline added here so the
  pattern doesn't recur.
- **Tests live under `${project.basedir}/test`,** not the standard
  `src/test/java`. The project's main source root is
  `${project.basedir}`, so the tests follow the same root convention.
  Surefire is configured for this layout in `pom.xml`.

### Known follow-ups 

- **CI/CD** — no GitHub Actions matrix yet (Linux/Windows/macOS
  `mvn package` + `mvn test`); Spotbugs/Checkstyle/OWASP gates
  unwired; `package-windows.bat` historically hard-coded a
  developer-machine Maven path.
- **Tighten `ObjectInputFilter` allowlist** from the `com.repdev.*`
  prefix to an exact class-name set once telemetry shows what's
  actually serialized.
- **POSIX permissions on `~/.repdev/*.log`** (chmod 600 + symlink
  guard) for multi-user-host deployments.
