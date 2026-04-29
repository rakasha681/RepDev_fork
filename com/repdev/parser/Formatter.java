package com.repdev.parser;

import java.util.ArrayList;
import java.util.Stack;

import com.repdev.Config;
import com.repdev.Indenter;

/**
 * Takes an arraylist of tokens and the required options, and generates a newly formatted repgen
 * @author poznanja
 *
 */
public class Formatter {
	ArrayList<Token> tokens;
	String oldFile = "", newFile = "";

	public Formatter(String oldFile, ArrayList<Token> tokens){
		this.tokens = tokens;
		this.oldFile = oldFile;
	}

	private String getWhitespaceAfter(Token cur){
		if( cur.getAfter() == null)
			return oldFile.substring(cur.getEnd());

		return oldFile.substring(cur.getEnd(),cur.getAfter().getStart());
	}

	/**
	 * Gets correctly capped string
	 * @param cur
	 * @return
	 */
	private String getCorrectTokenString(Token cur){
		return oldFile.substring(cur.getStart(),cur.getEnd());
	}

	private int contains(String[] list, String str){
		int i = 0;

		for( String test : list){
			if( test.equals(str))
				return i;

			i++;
		}

		return -1;
	}

	private boolean processBeforeAndAfter(StringBuilder str, Token cur, String indent, String nextIndent, boolean curEndClosesDo){
		String noSpaceBefore = "():.,%=+-/*<>";
		String noSpaceAfter = "(:.=+-/*$<>";
		String[] newLineAfter = { "do", "end" };
		// 2 newlines after END produces a blank line separator; drop to 1
		// when the user has turned off the blank-line-after-END option.
		int[] nNewLineAfter = { 1, Config.getBlankLineAfterEnd() ? 2 : 1 };

		String[] newLineBefore = { "do", "end" };
		int[] nNewLineBefore = { 1,1 };

		boolean addedNewline = false;
		int index;

		if( noSpaceAfter.contains(cur.getStr()) )
			;//For now, nothing
		else if( cur.getAfter() != null && noSpaceBefore.contains(cur.getAfter().getStr()))
			;
		else
			str.append(" ");

		if( (index = contains(newLineAfter,cur.getStr())) >= 0){
			// An END that closes a PROCEDURE / SETUP / SELECT / etc. — i.e.
			// anything that isn't a DO — falls through to the source-
			// whitespace path below so we don't force an extra blank line
			// between the division and whatever follows it.
			boolean skipForcedNewlines = cur.getStr().equals("end") && !curEndClosesDo;
			if (!skipForcedNewlines) {
				int offset = 0;

				//If statements, if there is an else, we don't want the second new line
				if( cur.getAfter() != null && (cur.getAfter().getStr().equals("else") || cur.getAfter().getStr().equals("end")))
					offset++;

				for( int i = 0; i < nNewLineAfter[index] - offset; i++)
					str.append("\n" + indent);

				addedNewline = true;
			}
		}

		if( cur.getAfter() != null && (index = contains(newLineBefore,cur.getAfter().getStr())) >= 0 ){
			for( int i = 0; i < nNewLineBefore[index]; i++)
				str.append("\n" + nextIndent);

			addedNewline = true;
		}

		if( !addedNewline){ //Default, add a space after it and continue

			if( getWhitespaceAfter(cur).contains("\n")){
				str.append(getWhitespaceAfter(cur).replaceAll(" ","").replaceAll("\t","").replaceAll("\n", "\n" + nextIndent));
			}
		}

		return true;
	}

	/**All the code formatting logic is in here*/
	public String getFormattedFile(){
		StringBuilder str = new StringBuilder();
		String indent = "", curStr;

		String tabStr = Indenter.getTabStr();
		int tabWidth = Config.getTabSize();
		// Split-indent only makes sense with a concrete tab width — with
		// tabSize=0 ("regular tabs") there is no half of a \t to emit.
		boolean split = Config.getSplitIndentDoBlocks() && tabWidth > 0;
		// In split mode each DO level always uses spaces for its body so the
		// half-tab header math lines up regardless of spaces-for-tabs.
		String fullTab = split ? repeatSpaces(tabWidth) : tabStr;
		String halfTab = split ? repeatSpaces(tabWidth / 2) : "";

		// Per-level head name: the token string of the realHead that opened
		// this block. Pushed on every realHead, popped at the matching realEnd.
		// Used to distinguish a DO-closing END (triggers the blank line) from
		// a division-closing END (falls through to source whitespace).
		Stack<String> blockStack = new Stack<String>();

		// Set on the iteration that pops for an upcoming END, and consumed on
		// the next iteration when cur is that END — lets processBeforeAndAfter
		// see what the END actually closed.
		boolean pendingEndClosesDo = false;

		Token after = null;

		//Go through each token, and write the proper output to a new str buffer
		for( Token cur : tokens ){
			boolean curEndClosesDo = pendingEndClosesDo;
			pendingEndClosesDo = false;

			after = cur.getAfter();
			str.append(getCorrectTokenString(cur));
			curStr = cur.getStr();

			//Modify indentation
			if( cur.isRealHead() ) {
				blockStack.push(curStr);
				indent += fullTab;
			}

			boolean poppedDoSplit = false;
			if( after != null && after.isRealEnd() ) {
				if (!blockStack.isEmpty()) {
					String popped = blockStack.pop();
					boolean poppedIsDo = popped.equals("do");
					poppedDoSplit = split && poppedIsDo;
					pendingEndClosesDo = poppedIsDo;
				}
				indent = indent.substring(0,Math.max(0,indent.length()-fullTab.length()));
			}

			// The indent printed ahead of `after` may need a half-tab bump
			// when `after` is the opening DO of a split-indent block or the
			// matching END that just popped one.
			String nextIndent = indent;
			if (split && after != null) {
				if (poppedDoSplit)
					nextIndent = indent + halfTab;
				else if (after.isRealHead() && after.getStr().equals("do"))
					nextIndent = indent + halfTab;
			}

			// The statement that follows a `then`, or a standalone `else`
			// (one not continued by `if`), is a single nested statement and
			// gets +1 indent. If that statement is itself a `do` we defer to
			// the DO rules above — otherwise we bump nextIndent by fullTab
			// for the newline leading into it. Subsequent lines reset
			// naturally because the check is re-evaluated each iteration.
			if (after != null
					&& !cur.inString() && cur.getCDepth() == 0 && !cur.inDate()
					&& !(after.isRealHead() && after.getStr().equals("do"))) {
				String cs = curStr;
				boolean triggers =
						cs.equals("then")
						|| (cs.equals("else") && !after.getStr().equals("if"));
				if (triggers)
					nextIndent = indent + fullTab;
			}


			if( !cur.inString() && cur.getCDepth() == 0 && !cur.inDate()){ //Plain old tokens
				processBeforeAndAfter(str, cur, indent, nextIndent, curEndClosesDo);
			}
			else if( cur.inString() && cur.getCDepth() == 0 && !cur.inDate()){ //Inside strings
				if( after != null && after.inString())
					str.append(getWhitespaceAfter(cur)); //Maintain existing formatting if we are in a string
				else {
					processBeforeAndAfter(str, cur, indent, nextIndent, curEndClosesDo);
				}
			}
			else if(cur.getCDepth() > 0) //In comments, don't format
				if( cur.isRealEnd() && curStr.equals("]"))
					processBeforeAndAfter(str, cur, indent, nextIndent, curEndClosesDo);
				else
					str.append(getWhitespaceAfter(cur));
		}

		return str.toString();
	}

	private static String repeatSpaces(int n) {
		if (n <= 0) return "";
		StringBuilder sb = new StringBuilder(n);
		for (int i = 0; i < n; i++) sb.append(' ');
		return sb.toString();
	}
}
