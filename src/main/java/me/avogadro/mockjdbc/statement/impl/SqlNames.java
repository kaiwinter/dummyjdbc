package me.avogadro.mockjdbc.statement.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads the table name of a SELECT, INSERT, UPDATE or DELETE with one identifier rule for all of them.
 * <p>
 * A name has 1 to 4 parts separated by <code>.</code> (<code>server.db.schema.table</code>). Each part is a plain word
 * (<code>[A-Za-z_][A-Za-z0-9_$]*</code>) or a quoted one (<code>[...]</code>, <code>"..."</code> or
 * <code>`...`</code>, with the <code>]]</code>, <code>""</code> and <code>``</code> escapes) whose content is used as a
 * single part: <code>[dbo.utenti]</code>, <code>[dbo].[utenti]</code> and <code>dbo.utenti</code> all give
 * <code>dbo.utenti</code>. Temporary tables (<code>#tmp</code>), table variables (<code>@rows</code>) and quoted names
 * with spaces, <code>?</code>, <code>#</code> or <code>,</code> (the separators of the lookup names) give no name.
 * <p>
 * Strings, comments and quoted identifiers are skipped when looking for keywords, so <code>'from x'</code> or
 * <code>-- from here</code> are never taken for a FROM.
 */
final class SqlNames {

	/** a plain (not quoted) part of a name */
	private static final Pattern PLAIN_PART = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

	/** characters that cannot be in a quoted part: they separate the parts of the lookup names */
	private static final Pattern INVALID_QUOTED_PART = Pattern.compile(".*[\\s?#,].*", Pattern.DOTALL);

	private static final int MAX_PARTS = 4;

	private static final char WORD = 'w';
	private static final char QUOTED = 'q';
	private static final char STRING = 's';
	private static final char SYMBOL = 'p';

	private static final class Token {
		final char type;
		final String text;

		Token(char type, String text) {
			this.type = type;
			this.text = text;
		}

		boolean isWord(String word) {
			return type == WORD && text.equalsIgnoreCase(word);
		}

		boolean isSymbol(char symbol) {
			return type == SYMBOL && text.charAt(0) == symbol;
		}
	}

	private SqlNames() {
	}

	/**
	 * @param sql the SQL text
	 * @return the name after the first FROM outside any parenthesis (looking inside a derived table
	 *         <code>FROM (SELECT ... FROM t)</code>), or <code>null</code>
	 */
	static String selectTable(String sql) {
		List<Token> tokens = tokenize(sql);
		return tokens == null ? null : firstFrom(tokens, 0);
	}

	/**
	 * @param sql the SQL text
	 * @return the table of an <code>INSERT [INTO] t</code>, <code>UPDATE t</code> or <code>DELETE [FROM] t</code>
	 *         (with or without the column list), or <code>null</code>
	 */
	static String writeTable(String sql) {
		List<Token> tokens = tokenize(sql);
		if (tokens == null || tokens.isEmpty()) {
			return null;
		}
		Token first = tokens.get(0);
		int start;
		if (first.isWord("insert")) {
			start = skipWord(tokens, 1, "into");
		} else if (first.isWord("update")) {
			start = 1;
		} else if (first.isWord("delete")) {
			start = skipWord(tokens, 1, "from");
		} else {
			return null;
		}
		return readName(tokens, start);
	}

	/**
	 * @param name a name returned by {@link #selectTable(String)} or {@link #writeTable(String)}
	 * @return the text after the last <code>.</code> (<code>utenti</code> for <code>dbo.utenti</code>), or
	 *         <code>null</code> if the name has no <code>.</code>
	 */
	static String lastPart(String name) {
		int dot = name.lastIndexOf('.');
		return dot < 0 ? null : name.substring(dot + 1);
	}

	private static int skipWord(List<Token> tokens, int index, String word) {
		return index < tokens.size() && tokens.get(index).isWord(word) ? index + 1 : index;
	}

	/** the name after the first FROM at the level of <code>start</code>; stops at the parenthesis that closes it */
	private static String firstFrom(List<Token> tokens, int start) {
		int depth = 0;
		for (int i = start; i < tokens.size(); i++) {
			Token token = tokens.get(i);
			if (token.isSymbol('(')) {
				depth++;
			} else if (token.isSymbol(')')) {
				if (--depth < 0) {
					return null;
				}
			} else if (depth == 0 && token.isWord("from")) {
				if (i + 1 < tokens.size() && tokens.get(i + 1).isSymbol('(')) {
					return firstFrom(tokens, i + 2);
				}
				return readName(tokens, i + 1);
			}
		}
		return null;
	}

	private static String readName(List<Token> tokens, int start) {
		StringBuilder name = new StringBuilder();
		int i = start;
		for (int parts = 1; parts <= MAX_PARTS; parts++) {
			if (i >= tokens.size() || !isNamePart(tokens.get(i))) {
				return null;
			}
			name.append(tokens.get(i).text);
			if (i + 1 < tokens.size() && tokens.get(i + 1).isSymbol('.')) {
				name.append('.');
				i += 2;
			} else {
				return name.toString();
			}
		}
		return null;
	}

	private static boolean isNamePart(Token token) {
		if (token.type == WORD) {
			return PLAIN_PART.matcher(token.text).matches();
		}
		return token.type == QUOTED && !token.text.isEmpty() && !INVALID_QUOTED_PART.matcher(token.text).matches();
	}

	/**
	 * @return the tokens of the SQL text without spaces and comments, or <code>null</code> if a string, quoted name or
	 *         comment is not closed
	 */
	private static List<Token> tokenize(String sql) {
		List<Token> tokens = new ArrayList<Token>();
		int length = sql.length();
		int i = 0;
		while (i < length) {
			char c = sql.charAt(i);
			if (Character.isWhitespace(c)) {
				i++;
			} else if (sql.startsWith("--", i)) {
				int end = sql.indexOf('\n', i);
				i = end < 0 ? length : end + 1;
			} else if (sql.startsWith("/*", i)) {
				int end = sql.indexOf("*/", i + 2);
				if (end < 0) {
					return null;
				}
				i = end + 2;
			} else if (c == '\'' || c == '"' || c == '`' || c == '[') {
				char close = c == '[' ? ']' : c;
				StringBuilder text = new StringBuilder();
				int j = i + 1;
				while (true) {
					if (j >= length) {
						return null;
					}
					char d = sql.charAt(j);
					if (d == close) {
						// a doubled closing character is an escaped one
						if (j + 1 < length && sql.charAt(j + 1) == close) {
							text.append(close);
							j += 2;
							continue;
						}
						break;
					}
					text.append(d);
					j++;
				}
				tokens.add(new Token(c == '\'' ? STRING : QUOTED, text.toString()));
				i = j + 1;
			} else if (isWordChar(c)) {
				int j = i;
				while (j < length && isWordChar(sql.charAt(j))) {
					j++;
				}
				tokens.add(new Token(WORD, sql.substring(i, j)));
				i = j;
			} else {
				tokens.add(new Token(SYMBOL, String.valueOf(c)));
				i++;
			}
		}
		return tokens;
	}

	/** <code>#</code> and <code>@</code> are kept in the word so that <code>#tmp</code> and <code>@rows</code> are rejected */
	private static boolean isWordChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '#' || c == '@';
	}
}
