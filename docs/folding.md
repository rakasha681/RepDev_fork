# Code Folding Guide

## Overview

Code folding lets you collapse and expand blocks of code to focus on what matters. RepDev folds any code region that spans more than one line and has a clear head/end boundary:

- **Block keywords:** `DEFINE...END`, `SETUP...END`, `DO...END`, `PROCEDURE...END`, `HEADERS...END`
- **Bracket comments:** `[...]` (when the opening `[` and closing `]` are on different lines)

Folding is a **view-only feature**. When you save, all hidden text is automatically reinserted into the file, so the on-disk file is always the complete source. Folding does not affect what gets saved.

## Keybindings

| Action | Keybinding | Alternative |
|--------|-----------|-------------|
| Collapse current region | `Ctrl+-` (minus/hyphen) | `Ctrl+Keypad-` |
| Expand current region | `Ctrl+=` (equals/plus) | `Ctrl+Keypad+` |
| Collapse all | `Ctrl+Shift+-` | — |
| Expand all | `Ctrl+Shift+=` | — |
| Toggle via gutter | Click the triangle in the fold column | — |

**Collapse** hides the body of the innermost region containing the caret. **Expand** reveals it. **Collapse/Expand all** affects every foldable region in the file.

## Gutter Triangles

The fold column (narrow gutter on the left, just inside the line numbers) displays fold indicators:

- **Down-pointing triangle ▼** — Region is expandable (body is currently visible). Click to collapse.
- **Right-pointing triangle ▶** — Region is collapsed (body is hidden). Click to expand.
- **No triangle** — Line is not a foldable head, or a fold boundary is malformed.

## Save-Time Reassembly

When you save the file, RepDev calls `getUnfoldedText()` to reassemble all hidden text back into the buffer before writing to disk. This means:

- **Folding is non-destructive.** Hidden code is kept in memory and reinserted at save time.
- **The on-disk file is always complete.** Opening the file in another editor shows all lines, folded or not.
- **Folding is a session-local view.** If you close and reopen the file, folds are reset to the default (all expanded).

## Find and Replace Integration

The Find/Replace dialog has an **"Include folded sections"** toggle in the Options group. When checked:

- Searches extend into hidden (collapsed) regions.
- When a match is found in hidden text, that region is automatically expanded so you can see the match.

When unchecked (default):
- Searches only scan visible text.
- Hidden regions are ignored.

This toggle is remembered across sessions in your `repdev.conf`.

## Unused-Variable Detection

Variables used inside collapsed regions are still counted towards usage. Specifically:

- Hidden text from collapsed `DEFINE...END` blocks is **excluded** from usage scans (because bodies of DEFINE blocks are declarations, not usages).
- Hidden text from collapsed bracket comments `[...]` is **excluded** from usage scans (because comments don't count as code usage).
- Hidden text from other folded regions (DO, SETUP, PROCEDURE, HEADERS, etc.) **is included** in usage scans.

This prevents false-positive "unused variable" warnings for variables that are declared or only discussed in comments but not actually used in active code.

## Edge Cases

### Orphan Bracket Closers

When you fold a bracket-comment region `[...]`, the system leaves the closing `]` visible at `headerLine + 1` so the syntax highlighter can still recognize the bracket-comment block. This orphan closer is explicitly skipped during fold-range computation to prevent mispairing with outer block boundaries.

Example:
```
[comment block
    that spans
    multiple lines
]
```

When folded, the display becomes:
```
[comment block ▶
]
```

The `]` remains visible but does not participate in fold pairing logic.

## Limitations

- **Folds are not persisted.** When you close and reopen a file, all regions are expanded. This is by design — folds are a transient view preference, not part of the file's logical state.
- **Folding while unsaved is fine.** You can fold, unfold, and edit freely without saving in between. Text is reassembled only when you save.
- **Partial folds in deeply nested code.** If you fold an inner region and its parent outer region, toggling the parent will expand both. This is expected: a parent expansion reveals all its children, including those you had folded.

## Performance

- **Fold-range recomputation is debounced.** After an edit, fold ranges are recalculated after 50 ms of inactivity. This prevents expensive recomputation on bulk pastes or rapid keystrokes.
- **Collapsed regions reduce parser load.** Unused-variable detection and other scans skip folded DEFINE blocks and bracket comments, reducing false positives and speeding up diagnostics.

## Troubleshooting

**"Collapse/Expand key doesn't work"**
- Ensure you are in the editor (not the Find dialog or another control).
- Check that the caret is on a line that is a foldable head (has a triangle in the gutter).

**"I folded something and can't find it now"**
- Use Expand All (`Ctrl+Shift+=`) to reveal everything.
- Or click the triangles in the gutter to expand individual regions.

**"Folding doesn't seem to affect saves"**
- This is correct. Folding is view-only. When you save, all hidden text is reinserted. You can verify this by closing and reopening the file — the full source is always there.
