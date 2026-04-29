/**
 *  RepDev - RepGen IDE for Symitar
 *  Copyright (C) 2007  Jake Poznanski, Ryan Schultz, Sean Delaney
 */

package com.repdev;

/**
 * Minimal seam used by {@link FoldingManager} to call back into its hosting
 * editor. The full {@link EditorComposite} is far too heavy and too entangled
 * with SWT to be useful in tests; this interface narrows the dependency to
 * exactly the two operations folding actually needs.
 *
 * <p>F3 step 1: extracted to break the bidirectional concrete coupling between
 * {@code FoldingManager} and {@code EditorComposite} flagged in the review's
 * P2 architecture findings. With this interface in place, the fold engine can
 * be exercised against an in-memory test host (a fake that records pushed
 * fold undos and returns a canned gutter width) without standing up a real
 * {@code EditorComposite}.</p>
 */
public interface EditorFoldHost {

	/**
	 * Width in pixels of the line-number gutter column. Folding uses this to
	 * size its own marker column relative to the gutter.
	 */
	int calcWidth();

	/**
	 * Record a fold operation in the editor's undo stack so Ctrl+Z reverses
	 * collapse/expand actions.
	 *
	 * @param op       one of {@code EditorComposite.FOLD_OP_COLLAPSE},
	 *                 {@code FOLD_OP_EXPAND}, {@code FOLD_OP_COLLAPSE_ALL},
	 *                 {@code FOLD_OP_EXPAND_ALL}
	 * @param foldLine the line that was folded; ignored for fold-all ops
	 */
	void pushFoldUndo(int op, int foldLine);

	/**
	 * Called by the fold engine once a batch of collapses/expands has
	 * settled (collapseAll / expandAll / undo replay). The host can use
	 * this to refresh editor view state that was suppressed during the
	 * batch — most importantly the current-line highlight, which would
	 * otherwise be left painted on a stale line index after dozens of
	 * line removals.
	 */
	void refreshAfterFoldBatch();
}
