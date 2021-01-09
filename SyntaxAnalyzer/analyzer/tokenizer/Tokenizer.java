package analyzer.tokenizer;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

// Handles file opening and tokenizing; removes all commands and white-space and 
// provides an API for reading a source file as a token stream
public class Tokenizer {

	private LinkedList<String> stream;
	private String currentToken;
	private Token tokenType;

	public static final List<String> keywords = Arrays.asList("class", "method", "function", "constructor", "int",
			"boolean", "char", "void", "var", "static", "field", "let", "do", "if", "else", "while", "return", "true",
			"false", "null", "this");
	public static final List<Character> symbols = Arrays.asList('{', '}', '(', ')', '[', ']', '.', ',', ';', '+', '-',
			'*', '/', '&', '|', '<', '>', '=', '~');

	/*
	 * Note: a more efficient/robust tokenizer would not just store all the tokens
	 * in memory! It would read some tokens at a time from the file, keeping track
	 * of the number of the line it last stopped. This implies more efficient memory
	 * usage because when we see malformed syntax, in a legit compiler, we'd throw
	 * an exception and all that extra work we initially did in reading/cleaning the
	 * file would be wasted. Did not do it this way b/c we only compile small
	 * programs and our implementation is quite naive in many other respects
	 */

	// consumes file name as String; must end in .jack
	public Tokenizer(String fileName) throws Exception {
		stream = new LinkedList<String>();

		Scanner reader = new Scanner(new File(fileName));
		String line;
		// needTerm is true iff we are inside an un-terminated multi-line comment
		boolean needTerm = false;

		while (reader.hasNextLine()) {
			line = reader.nextLine();

			// <-- Multi-line Comments -->
			// if we are in a comment, need to check if it terminates (and if it doesn't, we
			// skip the current line b/c comments are ignored)
			if (needTerm)
				if (line.contains("*/")) {
					line = line.substring(line.indexOf("*/") + 2, line.length());
					needTerm = false;
				} else {
					continue;
				}

			if (line.contains("/*"))
				// check if "multi-line" comment terminates on a single line (this is legal)
				if (line.contains("*/")) {
					line = line.substring(0, line.indexOf("/*"))
							+ line.substring(line.indexOf("*/") + 2, line.length());
				} else {
					line = line.substring(0, line.indexOf("/*"));
					needTerm = true;
				}

			// <-- Single-line Comments -->
			if (line.contains("//"))
				line = line.substring(0, line.indexOf("//"));

			line = line.trim().replaceAll("\\s+", " ");
			if (line.isEmpty())
				continue;

			stream.addAll(decompose(line));
		}

		for (String s : stream)
			System.out.println(s);

		reader.close();
	}

	// decompose given String into component tokens by splitting on spaces and
	// symbols
	private List<String> decompose(String line) throws Exception {
		List<String> tokens = new ArrayList<String>();
		String token = "";
		boolean stringLiteral = false;

		// not using switch because we have non-uniform cases
		for (char c : line.toCharArray()) {
			if (stringLiteral) {
				token += c;
				if (c == '\"') {
					tokens.add(token);
					token = "";
					stringLiteral = false;
				}
			} else if (c == '\"') {
				token += c;
				stringLiteral = true;
			} else if (c == ' ') {
				if (!token.isEmpty())
					tokens.add(token);
				token = "";
			} else if (symbols.contains(c)) {
				if (!token.isEmpty())
					tokens.add(token);
				token = "";
				tokens.add(String.valueOf(c));
			} else {
				token += c;
			}
		}

		if (stringLiteral)
			throw new Exception("Found unterminated string literal in '" + line + '\'');

		// grab token at end of the stream if there is one
		if (!token.isEmpty())
			tokens.add(token);

		return tokens;
	}

	// does the stream have any more tokens?
	public Boolean hasNext() {
		return stream.size() > 0;
	}

	// read the next token from the stream (we do not give the caller the token
	// because we want them to use the API we provide - results in less errors)
	// CONSTRAINT: only called if hasNext() produces true
	public void nextToken() {
		currentToken = stream.pop();
		tokenType = findTokenType();
	}

	// a look-ahead function
	public String peek() {
		return stream.peek();
	}

	// produce type of current token (private because we only want to figure out
	// the token type ONCE for any given token)
	private Token findTokenType() {
		if (currentToken.length() == 1 && symbols.contains(currentToken.charAt(0)))
			return Token.SYMBOL;
		else if (keywords.contains(currentToken))
			return Token.KEYWORD;
		else if (currentToken.startsWith("\""))
			return Token.STRING_CONST;

		try {
			Integer.parseInt(currentToken);
			return Token.INT_CONST;
		} catch (Exception e) {
			return Token.IDENTIFIER;
		}
	}

	public Token tokenType() {
		return tokenType;
	}

	// CONSTRAINT: only called when tokenType == SYMBOL
	public Character symbol() {
		return currentToken.charAt(0);
	}

	// CONSTRAINT: only called when tokentype = KEYWORD
	public Keyword keyword() throws Exception {
		switch (currentToken) {
		case "class":
			return Keyword.CLASS;
		case "method":
			return Keyword.METHOD;
		case "function":
			return Keyword.FUNCTION;
		case "constructor":
			return Keyword.CONSTRUCTOR;
		case "int":
			return Keyword.INT;
		case "boolean":
			return Keyword.BOOLEAN;
		case "char":
			return Keyword.CHAR;
		case "void":
			return Keyword.VOID;
		case "var":
			return Keyword.VAR;
		case "static":
			return Keyword.STATIC;
		case "field":
			return Keyword.FIELD;
		case "let":
			return Keyword.LET;
		case "do":
			return Keyword.DO;
		case "if":
			return Keyword.IF;
		case "else":
			return Keyword.ELSE;
		case "while":
			return Keyword.WHILE;
		case "return":
			return Keyword.RETURN;
		case "true":
			return Keyword.TRUE;
		case "false":
			return Keyword.FALSE;
		case "null":
			return Keyword.NULL;
		case "this":
			return Keyword.THIS;
		default:
			throw new Exception("Unknown keyword: '" + currentToken + '\'');
		}
	}

	// CONSTRAINT: only called when tokenType == INT_CONST
	public int intVal() {
		return Integer.parseInt(currentToken);
	}

	// only called when tokenType == IDENTIFIER | STRING_CONST, or when
	// throwing an exception
	public String val() {
		if (tokenType == Token.STRING_CONST)
			return currentToken.substring(1, currentToken.length() - 1);
		return currentToken;
	}

	// put a token back into the stream
	public void returnToken() {
		stream.addFirst(currentToken);
	}

}
