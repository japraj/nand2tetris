package analyzer;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

import analyzer.tokenizer.Keyword;
import analyzer.tokenizer.Token;
import analyzer.tokenizer.Tokenizer;

// translates Jack code to XML (and later, VM bytecode); handles file opening and 
// writing (provides a simple API). Instantiate a new Parser for each .jack file
public class Parser {

	// We sometimes use a technique of grabbing the next token from the stream over
	// and over and use it to generate code, until we find one that fails to satisfy
	// our conditions. (At which point, we drop this token back into the stream and
	// return). This "look-ahead" is only used at points where we can be sure the
	// stream has excess tokens (ex. var declarations, because they are always
	// present at the top of a subroutine or class) and therefore is permissible.

	// Note that we could use peek() for the same functionality above but that does
	// not give us access to the tokenizer API/abstractions (ex. the tokenType() and
	// keyword() functions) while having the same risk of out of bounds
	// exception/segfaulting. peek() is better in some cases though.

	// Benefit of using a string builder and decoupling the write/addLine operations
	// is we could use try catch blocks to try adding particular blocks (and on
	// exception, just assume that the block is not present) without committing
	// incomplete outputs to the file

	private Tokenizer tokenizer;
	private FileWriter writer;
	private String fileName;

	// consumes name of file to parse (should have .jack extension)
	public Parser(String fileName) throws Exception {
		tokenizer = new Tokenizer(fileName);
		writer = new FileWriter(fileName.substring(0, fileName.length() - 4) + "xml");
		this.fileName = fileName;
	}

	private void addLine(String line) throws IOException {
		writer.write(line + '\n');
	}

	public void close() throws IOException {
		writer.close();
	}

	// try advancing tokenizer
	private void advance() throws Exception {
		if (tokenizer.hasNext())
			tokenizer.nextToken();
		else
			throw new Exception("Ran out of tokens");
	}

	// all of the 'assert<...>' functions below advance the tokenizer and check
	// whether the new token satisfies the imposed criteria; if it does not, an
	// exception is thrown. Results in easily readable code and eliminates
	// repetition. If we wanted more descriptive error messages, we could store
	// line numbers and the original line in our parser.
	private void assertToken(Token tokenType) throws Exception {
		advance();
		if (tokenizer.tokenType() != tokenType)
			throw new Exception("Expected token " + tokenType + " in " + fileName + ", found " + tokenizer.val());
	}

	private void assertKeyword(Keyword keywordType) throws Exception {
		advance();
		if (tokenizer.tokenType() != Token.KEYWORD || tokenizer.keyword() != keywordType)
			throw new Exception("Expected keyword " + keywordType + " in " + fileName + ", found " + tokenizer.val());
	}

	private void assertSymbol(char symbol) throws Exception {
		advance();
		if (tokenizer.tokenType() != Token.SYMBOL || tokenizer.symbol() != symbol)
			throw new Exception("Expected symbol " + symbol + " in " + fileName + ", found " + tokenizer.val());

	}

	// tries to add identifier to output
	private void addIdentifier() throws Exception {
		assertToken(Token.IDENTIFIER);
		addLine("<identifier>" + tokenizer.val() + "</identifier>");
	}

	// tries to add specified symbol to output; also tries to map reserved
	// characters to their conventional variants (ex. '<' is a reserved character in
	// XML, so it is replaced with '&lt;')
	private void addSymbol(char symbol, boolean advance) throws Exception {
		if (advance)
			assertSymbol(symbol);
		String representation = tokenizer.symbol().toString();
		switch (representation) {
		case "<":
			representation = "&lt;";
			break;
		case ">":
			representation = "&gt;";
			break;
		case "&":
			representation = "&amp;";
			break;
		case "\"":
			representation = "&quot;";
			break;
		}
		addLine("<symbol>" + representation + "</symbol>");
	}

	private void addSymbol(char symbol) throws Exception {
		addSymbol(symbol, true);
	}

	// adds next token as a symbol, regardless of what it is (wildcard selector)
	// CONSTRAINT: caller must ensure that token is symbol prior to calling this
	private void addSymbol() throws Exception {
		addSymbol('.', false);
	}

	private void addType(boolean advance) throws Exception {
		if (advance)
			advance();
		if (tokenizer.tokenType() == Token.KEYWORD)
			addLine("<keyword>" + tokenizer.keyword().toString().toLowerCase() + "</keyword>");
		else
			addLine("<identifier>" + tokenizer.val() + "</identifier>");
	}

	private void addType() throws Exception {
		addType(true);
	}

	// compile a complete class; this is the 'init' function
	public void compileClass() throws Exception {
		assertKeyword(Keyword.CLASS);
		addLine("<class>");
		addLine("<keyword>class</keyword>");

		addIdentifier();

		addSymbol('{');

		compileClassVarDec();

		compileSubroutine();

		addSymbol('}');

		addLine("</class>");
	}

	// compile a static declaration or field declaration
	private void compileClassVarDec() throws Exception {
		// classVarDec format: ('static'|'field') type varName (',' varName)* ';'
		advance();
		while (tokenizer.tokenType() == Token.KEYWORD
				&& (tokenizer.keyword() == Keyword.STATIC || tokenizer.keyword() == Keyword.FIELD)) {
			addLine("<classVarDec>");
			addLine("<keyword>" + tokenizer.keyword().toString().toLowerCase() + "</keyword>");

			addType();
			addIdentifier();

			// optional varName list
			advance();
			while (tokenizer.tokenType() == Token.SYMBOL && tokenizer.symbol() == ',') {
				addLine("<symbol>" + tokenizer.symbol().toString() + "</symbol>");
				addIdentifier();
				advance();
			}
			tokenizer.returnToken();

			addSymbol(';');
			addLine("</classVarDec>");
			advance();
		}
		tokenizer.returnToken();
	}

	private boolean isSubroutine() throws Exception {
		return tokenizer.tokenType() == Token.KEYWORD
				&& Arrays.asList(Keyword.CONSTRUCTOR, Keyword.FUNCTION, Keyword.METHOD).contains(tokenizer.keyword());
	}

	// compile a complete method, function, or constructor (methods vs. functions -
	// functions do not use local fields of a class - they are 'static')
	private void compileSubroutine() throws Exception {
		// ('constructor'|'function'|'method') ('void' | type) subroutineName
		// '(' parameterList ')' '{' varDec* statements '}'

		advance();

		while (isSubroutine()) {
			addLine("<subroutineDec>");
			// constructor | function | method
			addLine("<keyword>" + tokenizer.keyword().toString().toLowerCase() + "</keyword>");

			addType();
			addIdentifier();

			addSymbol('(');
			compileParameterList();
			addSymbol(')');

			addLine("<subroutineBody>");
			addSymbol('{');
			compileVarDec();
			compileStatements();
			addSymbol('}');
			addLine("</subroutineBody>");

			addLine("</subroutineDec>");
			advance();
		}

		tokenizer.returnToken();

	}

	// compile a (possibly empty) parameter list, not including the
	// enclosing parenthesis
	private void compileParameterList() throws Exception {
		// ((type varName) (',' type varName)*)?

		advance();
		addLine("<parameterList>");
		if (tokenizer.tokenType() != Token.SYMBOL) {
			addType(false);
			addIdentifier();

			// optionalList
			advance();
			while (tokenizer.tokenType() == Token.SYMBOL && tokenizer.symbol() == ',') {
				addLine("<symbol>,</symbol>");
				addType();
				addIdentifier();

				advance();
			}
			tokenizer.returnToken();
		} else {
			tokenizer.returnToken();
		}
		addLine("</parameterList>");
	}

	// compile a variable declaration
	private void compileVarDec() throws Exception {
		// 'var' type varName (',' varName)* ';'

		advance();
		while (tokenizer.tokenType() == Token.KEYWORD && tokenizer.keyword() == Keyword.VAR) {
			addLine("<varDec>");

			addLine("<keyword>var</keyword>");
			addType();
			addIdentifier();

			// optionalList
			advance();
			while (tokenizer.tokenType() == Token.SYMBOL && tokenizer.symbol() == ',') {
				addLine("<symbol>,</symbol>");
				addIdentifier();

				advance();
			}
			tokenizer.returnToken();

			addSymbol(';');
			addLine("</varDec>");
			advance();
		}
		tokenizer.returnToken();
	}

	// produces true if the current token is a statement
	private Boolean isStatement() throws Exception {
		return tokenizer.tokenType() == Token.KEYWORD
				&& Arrays.asList(Keyword.LET, Keyword.DO, Keyword.IF, Keyword.WHILE, Keyword.RETURN)
						.contains(tokenizer.keyword());
	}

	// compile a sequence of statements, not including the enclosing
	// curly braces
	private void compileStatements() throws Exception {
		advance();
		if (isStatement()) {
			addLine("<statements>");
			while (isStatement()) {
				switch (tokenizer.keyword()) {
				case LET:
					compileLet();
					break;
				case DO:
					compileDo();
					break;
				case IF:
					compileIf();
					break;
				case WHILE:
					compileWhile();
					break;
				case RETURN:
					compileReturn();
					break;
				default:
					throw new Exception("Invalid usage of keyword '" + tokenizer.keyword() + '\'');
				}
				advance();
			}
			addLine("</statements>");
		}
		tokenizer.returnToken();
	}

	// compile a do statement
	// ASSUME: current token is 'do'
	private void compileDo() throws Exception {
		// 'do' subroutineCall ';'
		addLine("<doStatement>");
		addLine("<keyword>do</keyword>");
		compileSubroutineCall();
		addSymbol(';');
		addLine("</doStatement>");
	}

	// compile a subroutine call
	private void compileSubroutineCall() throws Exception {
		// identifier (x | '.' identifier x)
		// x = '(' expressionList ')'
		addIdentifier();

		advance();
		if (tokenizer.tokenType() != Token.SYMBOL)
			throw new Exception("Expected Symbol");
		if (tokenizer.symbol() == '.') {
			addLine("<symbol>.</symbol>");
			addIdentifier();
		} else {
			tokenizer.returnToken();
		}

		addSymbol('(');
		compileExpressionList();
		addSymbol(')');
	}

	// compile a let statement
	// ASSUME: current token is 'let'
	private void compileLet() throws Exception {
		// 'let' varName ('[' expression ']')? '=' expression ';'
		addLine("<letStatement>");
		addLine("<keyword>let</keyword>");
		addIdentifier();
		if (tokenizer.peek().equals("[")) {
			addSymbol('[');
			compileExpression();
			addSymbol(']');
		}
		addSymbol('=');
		compileExpression();
		addSymbol(';');
		addLine("</letStatement>");
	}

	// compile a while statement
	// ASSUME: current token is 'while'
	private void compileWhile() throws Exception {
		// 'while' '(' expression ')' '{' statements '}'
		addLine("<whileStatement>");
		addLine("<keyword>while</keyword>");
		addSymbol('(');
		compileExpression();
		addSymbol(')');
		addSymbol('{');
		compileStatements();
		addSymbol('}');
		addLine("</whileStatement>");
	}

	// compile a return statement
	// ASSUME: current token is 'return'
	private void compileReturn() throws Exception {
		// 'return' expression? ';'
		addLine("<returnStatement>");
		addLine("<keyword>return</keyword>");
		if (!tokenizer.peek().equals(";")) {
			compileExpression();
		}
		addSymbol(';');
		addLine("</returnStatement>");
	}

	// compile an if statement with optional else clause
	// ASSUME: current token is 'if'
	private void compileIf() throws Exception {
		// 'if' '(' expression ')' '{' statements '}' ('else' '{' statements '}')?
		addLine("<ifStatement>");
		addLine("<keyword>if</keyword>");
		addSymbol('(');
		compileExpression();
		addSymbol(')');
		addSymbol('{');
		compileStatements();
		addSymbol('}');
		if (tokenizer.peek().equals("else")) {
			advance();
			addLine("<keyword>else</keyword>");
			addSymbol('{');
			compileStatements();
			addSymbol('}');
		}
		addLine("</ifStatement>");
	}

	private Boolean isOperation(String s) {
		// op: '+'|'-'|'*'|'/'|'&'|'|'|'<'|'>'|'='
		return s.length() == 1 && Arrays.asList('+', '-', '*', '/', '&', '|', '<', '>', '=').contains(s.charAt(0));
	}

	// compile an expression
	private void compileExpression() throws Exception {
		// term (op term)*
		addLine("<expression>");
		compileTerm();
		while (isOperation(tokenizer.peek())) {
			advance();
			addSymbol();
			compileTerm();
		}
		addLine("</expression>");
	}

	// compile a term - uses a peek/look-ahead to distinguish between various
	// regions of the grammar (in particular, '[', '(', '.' determine which rules
	// we use)
	private void compileTerm() throws Exception {
		// unaryOp: '-'|'~'
		// term: integerConstant | stringConstant | keywordConstant |
		// varName | varName '[' expression ']' | subroutineCall |
		// '(' expression ')' | unaryOp term
		// KeywordConstant: 'true'|'false'|'null'|'this'
		addLine("<term>");
		advance();
		switch (tokenizer.tokenType()) {
		case SYMBOL:
			if (tokenizer.symbol() == '(') {
				// '(' expression ')'
				addSymbol();
				compileExpression();
				addSymbol(')');
			} else {
				// unaryOp term
				addSymbol();
				compileTerm();
			}
			break;
		case INT_CONST:
			addLine("<integerConstant>" + tokenizer.intVal() + "</integerConstant>");
			break;
		case STRING_CONST:
			addLine("<stringConstant>" + tokenizer.val() + "</stringConstant>");
			break;
		case IDENTIFIER:
			addLine("<identifier>" + tokenizer.val() + "</identifier>");
			switch (tokenizer.peek()) {
			case "[":
				// array access
				addSymbol('[');
				compileExpression();
				addSymbol(']');
				break;
			case "(":
				compileSubroutineCall();
				break;
			case ".":
				addSymbol('.');
				compileSubroutineCall();
				break;
			// else, its just varName
			}
			break;
		case KEYWORD:
			addLine("<keyword>" + tokenizer.keyword().toString().toLowerCase() + "</keyword>");
			break;
		}

		addLine("</term>");
	}

	// compile a (possibly empty) comma-separated list of expressions
	private void compileExpressionList() throws Exception {
		// (expression (',' expression)* )?
		addLine("<expressionList>");
		if (!tokenizer.peek().equals(")")) {
			compileExpression();
			while (!tokenizer.peek().equals(")")) {
				addSymbol(',');
				compileExpression();
			}
		}
		addLine("</expressionList>");
	}

}
