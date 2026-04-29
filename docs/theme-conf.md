# theme.conf User Guide

## Overview

`theme.conf` is a sidecar configuration file that stores your theme preferences separately from the main RepDev configuration. This separation means you can upgrade or downgrade RepDev without losing your theme choices, and the file can be safely edited by hand or deleted to reset to defaults.

## Location

`theme.conf` lives at the root of your RepDev installation directory (the same directory as `repdev.conf`).

## File Format

The file is a standard Java Properties file (plain text, line-based key=value pairs). Comments start with `#`.

Example:
```
# RepDev theme settings - edited by ThemeService
themeId=nord
themeMode=CUSTOM
systemLightThemeId=solarized-light
systemDarkThemeId=dark
```

## Configuration Keys

| Key | Meaning | Valid Values | Default | Example |
|-----|---------|--------------|---------|---------|
| `themeId` | Active theme when mode is CUSTOM | Any theme id (filename without `.xml`) | `default` | `themeId=nord` |
| `themeMode` | How themes are selected | `CUSTOM`, `SYSTEM` | `CUSTOM` | `themeMode=SYSTEM` |
| `systemLightThemeId` | Theme used when OS is in light mode (only if `themeMode=SYSTEM`) | Any theme id | `default` | `systemLightThemeId=solarized-light` |
| `systemDarkThemeId` | Theme used when OS is in dark mode (only if `themeMode=SYSTEM`) | Any theme id | `dark` | `systemDarkThemeId=dracula` |
| `lastKnownSystemTheme` | Internal tracking of the current OS appearance | Not user-editable | N/A | (auto-managed) |

## Mode: CUSTOM vs SYSTEM

**CUSTOM mode** (default):
- RepDev uses the theme in `themeId` regardless of your OS appearance setting.
- Example: `themeMode=CUSTOM` with `themeId=nord` always shows the Nord theme.

**SYSTEM mode**:
- RepDev follows your OS light/dark preference and switches automatically.
- When your OS is in light mode, the theme in `systemLightThemeId` is loaded.
- When your OS is in dark mode, the theme in `systemDarkThemeId` is loaded.
- Example: `themeMode=SYSTEM` with `systemLightThemeId=solarized-light` and `systemDarkThemeId=solarized-dark` switches themes as you toggle your OS appearance.

## Examples

### Custom Mode (Manual Theme Selection)

```
themeId=nord
themeMode=CUSTOM
systemLightThemeId=default
systemDarkThemeId=dark
```

In this configuration, RepDev always uses the Nord theme, regardless of your OS settings.

### System Mode (OS-Aware Switching)

```
themeId=default
themeMode=SYSTEM
systemLightThemeId=solarized-light
systemDarkThemeId=solarized-dark
```

In this configuration, RepDev switches between Solarized Light and Solarized Dark automatically when you change your OS appearance setting.

## Manual Editing

It is safe to edit `theme.conf` in your preferred text editor. However:

- **Do** use valid theme ids (check `styles/` directory for available `.xml` files; the id is the filename without the extension).
- **Don't** use a theme id that does not exist in `styles/` — RepDev will fail to load it and fall back to `default`.
- **Don't** change `lastKnownSystemTheme` manually; it is maintained automatically.

If you make a mistake and RepDev fails to start, the easiest fix is to delete `theme.conf` entirely. On the next startup, RepDev will generate a fresh file with defaults.

## Backup and Rollback

RepDev maintains an automatic backup when writing `theme.conf`:

- **Active file:** `theme.conf`
- **Backup file:** `theme.conf.bak`

If `theme.conf` becomes corrupted or you want to roll back to the previous state, you can:

1. Delete `theme.conf`.
2. Rename `theme.conf.bak` back to `theme.conf`.
3. Restart RepDev.

The backup is rotated each time RepDev writes the file, so the `.bak` file always contains the most recent stable state.

## Available Themes

Run the Options dialog (Ctrl+Shift+O) to see all installed themes. Theme ids correspond to `.xml` filenames in the `styles/` directory. Common examples:

- **Light themes:** `default`, `solarized-light`, `mint-light`, `platinum-light`, `ocean-light`
- **Dark themes:** `dark`, `nord`, `solarized-dark`, `midnight-navy`, `dracula` (if installed)
