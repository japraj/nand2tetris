package compiler;

import java.util.Arrays;

import compiler.tokenizer.Keyword;
import compiler.tokenizer.Token;
import compiler.tokenizer.Tokenizer;
import symboltable.Kind;
import symboltable.SymbolTable;
import writer.Command;
import writer.Segment;
import writer.VMWriter;

// translates Jack code to XML (and later, VM bytecode); handles file opening and 
// writing (provides a simple API). Instantiate a new Parser for each .jack file
public class CompilationEngine {

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
	private SymbolTable symbolTable;
	private VMWriter writer;

	private String fileName;
	private String className;

	private short labelCtr;
	private int args;

	// consumes name of file to parse (should have .jack extension)
	public CompilationEngine(String fileName) throws Exception {
		tokenizer = new Tokenizer(fileName);
		symbolTable = new SymbolTable();
		writer = new VMWriter(fileName);

		this.fileName = fileName;

		labelCtr = 0;
	}

	public void run() throws Exception {
		compileClass();
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

	// consumes an arbitrary number of token types; if the next token's type is not
	// equal to any of these, we throw an exception
	private void assertToken(Token... tokenTypes) throws Exception {
		advance();
		if (!Arrays.asList(tokenTypes).contains(tokenizer.tokenType()))
			throw new Exception(
					"Expected token " + Arrays.toString(tokenTypes) + " in " + fileName + ", found " + tokenizer.val());
	}

	private void assertKeyword(Keyword keywordType) throws Exception {
		advance();
		if (tokenizer.tokenType() != Token.KEYWORD || tokenizer.keyword() != keywordType)
			throw new Exception("Expected keyword " + keywordType + " in " + fileName + ", found " + tokenizer.val());
	}

	// parameter specifies the value of the next token (and the call implicitly
	// specifies the type to be SYMBOL)
	private void assertSymbol(char symbol) throws Exception {
		advance();
		if (tokenizer.tokenType() != Token.SYMBOL || tokenizer.symbol() != symbol)
			throw new Exception("Expected symbol " + symbol + " in " + fileName + ", found " + tokenizer.val());
	}

	private Segment varToSegment(String symbol) throws Exception {
		switch (symbolTable.kindOf(symbol)) {
		case STATIC:
			return Segment.STATIC;
		case FIELD:
			return Segment.THIS;
		case VAR:
			return Segment.LOCAL;
		case ARG:
			return Segment.ARGUMENT;
		case NONE:
		default:
			throw new Exception("Symbol '" + symbol + "' not found");
		}
	}

	// compile a complete class; this is the 'init' function
	private void compileClass() throws Exception {
		// class identifier { classVarDec* subroutine* }
		assertKeyword(Keyword.CLASS);

		assertToken(Token.IDENTIFIER);
		className = tokenizer.val();

		assertSymbol('{');

		compileClassVarDec();

		compileSubroutine();

		assertSymbol('}');

	}

	// compile a static declaration or field declaration
	private void compileClassVarDec() throws Exception {
		// classVarDec format: ('static'|'field') type varName (',' varName)* ';'
		advance();
		while (tokenizer.tokenType() == Token.KEYWORD
				&& (tokenizer.keyword() == Keyword.STATIC || tokenizer.keyword() == Keyword.FIELD)) {
			Keyword kind = tokenizer.keyword();

			assertToken(Token.KEYWORD, Token.IDENTIFIER);
			String type = tokenizer.val();

			assertToken(Token.IDENTIFIER);
			symbolTable.define(tokenizer.val(), type, Kind.valueOf(kind.toString()));

			// optional varName list
			advance(); // either comma (for continuation) or semicolon (for termination)
			while (tokenizer.tokenType() == Token.SYMBOL && tokenizer.symbol() == ',') {
				assertToken(Token.IDENTIFIER);
				symbolTable.define(tokenizer.val(), type, Kind.valueOf(kind.toString()));

				advance();
			}

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
			// constructor | function | method, current token at this line
			Keyword subroutineType = tokenizer.keyword();

			symbolTable.startNewSubroutine(subroutineType == Keyword.METHOD);

			assertToken(Token.KEYWORD, Token.IDENTIFIER); // return type

			assertToken(Token.IDENTIFIER);
			String name = tokenizer.val();

			assertSymbol('(');
			compileParameterList();
			assertSymbol(')');

			assertSymbol('{');
			compileVarDec();

			int numLocalVars = symbolTable.varCount(Kind.VAR);

			writer.writeFunction(className + '.' + name, numLocalVars);

			if (subroutineType == Keyword.METHOD) {
				// first arg should be a ref to the base address of the obj we are operating on
				// - set the virtual 'this' segment to align with the specified memseg
				writer.writePush(Segment.ARGUMENT, 0);
				writer.writePop(Segment.POINTER, 0);
			} else if (subroutineType == Keyword.CONSTRUCTOR) {
				// allocate memory for the current object and set the 'this' ptr to the base
				// address of the allocated memory segment
				writer.writePush(Segment.CONSTANT, symbolTable.varCount(Kind.FIELD));
				writer.writeCall("Memory.alloc", 1);
				writer.writePop(Segment.POINTER, 0);
			}

			compileStatements();
			assertSymbol('}');

			advance();
		}

		tokenizer.returnToken();

	}

	// compile a (possibly empty) parameter list, not including the
	// enclosing parenthesis
	private void compileParameterList() throws Exception {
		// ((type varName) (',' type varName)*)?
		advance();

		if (tokenizer.tokenType() != Token.SYMBOL) {
			String type = tokenizer.val();

			assertToken(Token.IDENTIFIER);
			symbolTable.define(tokenizer.val(), type, Kind.ARG);

			// optionalList
			advance();
			while (tokenizer.tokenType() == Token.SYMBOL && tokenizer.symbol() == ',') {
				assertToken(Token.KEYWORD, Token.IDENTIFIER);
				type = tokenizer.val();

				assertToken(Token.IDENTIFIER);
				symbolTable.define(tokenizer.val(), type, Kind.ARG);

				advance();
			}
		}

		tokenizer.returnToken();
	}

	// compile a variable declaration
	private void compileVarDec() throws Exception {
		// 'var' type varName (',' varName)* ';'
		advance();

		while (tokenizer.tokenType() == Token.KEYWORD && tokenizer.keyword() == Keyword.VAR) {
			assertToken(Token.KEYWORD, Token.IDENTIFIER);
			String type = tokenizer.val();

			assertToken(Token.IDENTIFIER);
			symbolTable.define(tokenizer.val(), type, Kind.VAR);

			// optionalList
			advance();
			while (tokenizer.tokenType() == Token.SYMBOL && tokenizer.symbol() == ',') {
				assertToken(Token.IDENTIFIER);
				symbolTable.define(tokenizer.val(), type, Kind.VAR);

				advance();
			}

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
				throw new Exception("Invalid keyword in statement");
			}
			advance();
		}
		tokenizer.returnToken();
	}

	// compile a do statement
	// ASSUME: current token is 'do'
	private void compileDo() throws Exception {
		// 'do' subroutineCall ';'
		compileSubroutineCall();
		assertSymbol(';');
		// do statements do not use the value left on the stack (the typical use case is
		// in fact for calling void functions, which push a 0 on the stack when
		// returning), so we must pop the result before releasing control
		writer.writePop(Segment.TEMP, 0);
	}

	// compile a subroutine call
	private void compileSubroutineCall() throws Exception {
		// identifier (x | '.' identifier x)
		// x = '(' expressionList ')'

		args = 0;

		assertToken(Token.IDENTIFIER);
		String subroutine = tokenizer.val();
		Boolean methodCall = tokenizer.peek().equals(".");
		Boolean extraArg = false;

		if (methodCall) {
			// need to set 'this' to point at the object whose name is currently stored
			// in the subroutine variable
			try {
				Segment ptr = varToSegment(subroutine);
				writer.writePush(ptr, symbolTable.indexOf(subroutine));
				extraArg = true;
			} catch (Exception e) {
				// if the identifier is not found, then we have a constructor or function, not a
				// method
				methodCall = false;
			}
			assertSymbol('.');
			assertToken(Token.IDENTIFIER);
			if (methodCall)
				subroutine = symbolTable.typeOf(subroutine) + '.' + tokenizer.val();
			else
				subroutine += '.' + tokenizer.val();
		} else {
			writer.writePush(Segment.POINTER, 0);
			subroutine = className + "." + subroutine;
			extraArg = true;
		}

		assertSymbol('(');
		args = 0;
		compileExpressionList();
		assertSymbol(')');

		writer.writeCall(subroutine, args + (extraArg ? 1 : 0));
	}

	// compile a let statement
	// ASSUME: current token is 'let'
	private void compileLet() throws Exception {
		// 'let' varName ('[' expression ']')? '=' expression ';'
		assertToken(Token.IDENTIFIER);
		// tokenizer.val() is the name of variable we are assigning the value to
		int index = symbolTable.indexOf(tokenizer.val());
		Segment seg = varToSegment(tokenizer.val());
		Boolean arrayEntry = false;

		if (tokenizer.peek().equals("[")) {
			arrayEntry = true;
			// branch for array indexing; we need to align the virtual 'that' segment so
			// that its 0 index is the array entry
			assertSymbol('[');

			writer.writePush(seg, index); // push base address
			compileExpression(); // push the index we want to assign to onto the stack
			writer.writeArithmetic(Command.ADD); // get adjusted address
			// writer.writePop(Segment.POINTER, 1); // align 'that' seg

			assertSymbol(']');
		}

		assertSymbol('=');
		compileExpression(); // push the value of the expr onto the stack

		if (arrayEntry) {
			writer.writePop(Segment.TEMP, 0);
			writer.writePop(Segment.POINTER, 1);
			writer.writePush(Segment.TEMP, 0);
			writer.writePop(Segment.THAT, 0);
		} else {
			writer.writePop(seg, index);
		}

		assertSymbol(';');
	}

	// compile a while statement
	// ASSUME: current token is 'while'
	private void compileWhile() throws Exception {
		// 'while' '(' expression ')' '{' statements '}'
		assertSymbol('(');

		// compile "while (cond) statements)" to:
		// label l1
		// ~cond
		// if-goto l2
		// <statements>
		// goto l1
		// label l2

		String l1 = "whileLabel0_" + labelCtr;
		String l2 = "whileLabel1_" + labelCtr;
		labelCtr++;

		writer.writeLabel(l1);

		compileExpression();
		writer.writeArithmetic(Command.NOT);

		writer.writeIf(l2);

		assertSymbol(')');
		assertSymbol('{');
		compileStatements();
		assertSymbol('}');

		writer.writeGoto(l1);
		writer.writeLabel(l2);
	}

	// compile a return statement
	// ASSUME: current token is 'return'
	private void compileReturn() throws Exception {
		// 'return' expression? ';'
		if (!tokenizer.peek().equals(";"))
			compileExpression();
		else
			writer.writePush(Segment.CONSTANT, 0);
		writer.writeReturn();
		assertSymbol(';');
	}

	// compile an if statement with optional else clause
	// ASSUME: current token is 'if'
	private void compileIf() throws Exception {
		// 'if' '(' expression ')' '{' statements '}' ('else' '{' statements '}')?

		String l1 = "ifLabel0_" + labelCtr;

		// ~cond
		assertSymbol('(');
		compileExpression();
		writer.writeArithmetic(Command.NOT);
		assertSymbol(')');

		writer.writeIf(l1);

		assertSymbol('{');
		compileStatements();
		assertSymbol('}');

		if (tokenizer.peek().equals("else")) {
			String l2 = "ifLabel1_" + labelCtr;
			writer.writeGoto(l2);

			writer.writeLabel(l1);

			advance();

			assertSymbol('{');
			compileStatements();
			assertSymbol('}');

			writer.writeLabel(l2);
		} else {
			writer.writeLabel(l1);
		}

		labelCtr++;
	}

	private Boolean isOperation(String s) {
		// op: '+'|'-'|'*'|'/'|'&'|'|'|'<'|'>'|'='
		return s.length() == 1 && Arrays.asList('+', '-', '*', '/', '&', '|', '<', '>', '=').contains(s.charAt(0));
	}

	// compile an expression
	private void compileExpression() throws Exception {
		// term (op term)*
		compileTerm();
		while (isOperation(tokenizer.peek())) {
			advance();
			char op = tokenizer.symbol();

			compileTerm();
			// ADD, SUB, NEG, EQ, GT, LT, AND, OR, NOT
			// op: '+'|'-'|'*'|'/'|'&'|'|'|'<'|'>'|'='
			Command command = Command.ADD;
			Boolean arithmetic = true;
			switch (op) {
			case '+':
				command = Command.ADD;
				break;
			case '-':
				command = Command.SUB;
				break;
			case '&':
				command = Command.AND;
				break;
			case '|':
				command = Command.OR;
				break;
			case '<':
				command = Command.LT;
				break;
			case '>':
				command = Command.GT;
				break;
			case '=':
				command = Command.EQ;
				break;
			case '*':
			case '/':
				arithmetic = false;
				break;
			default:
				throw new Exception("Unknown operation '" + op + '\'');
			}

			if (arithmetic)
				writer.writeArithmetic(command);
			else
				writer.writeCall("Math." + (op == '*' ? "multiply" : "divide"), 2);
		}
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
		advance();
		switch (tokenizer.tokenType()) {
		case SYMBOL:
			if (tokenizer.symbol() == '(') {
				// '(' expression ')'
				compileExpression();
				assertSymbol(')');
			} else {
				// unaryOp term
				Command cmd = tokenizer.symbol() == '~' ? Command.NOT : Command.NEG;
				compileTerm();
				writer.writeArithmetic(cmd);
			}
			break;
		case INT_CONST:
			writer.writePush(Segment.CONSTANT, tokenizer.intVal());
			break;
		case STRING_CONST:
			writer.writePush(Segment.CONSTANT, tokenizer.val().length());
			writer.writeCall("String.new", 1);
			for (char c : tokenizer.val().toCharArray()) {
				writer.writePush(Segment.CONSTANT, (int) c);
				writer.writeCall("String.appendChar", 2);
			}
			break;
		case IDENTIFIER:
			switch (tokenizer.peek()) {
			case "[":
				writer.writePush(varToSegment(tokenizer.val()), symbolTable.indexOf(tokenizer.val()));
				// array access
				assertSymbol('[');
				compileExpression();
				assertSymbol(']');
				writer.writeArithmetic(Command.ADD);
				writer.writePop(Segment.POINTER, 1);
				writer.writePush(Segment.THAT, 0);
				break;
			case "(":
			case ".":
				tokenizer.returnToken();
				compileSubroutineCall();
				break;
			default:
				// default is we want to push the value of the symbol with the given name onto
				// the stack
				writer.writePush(varToSegment(tokenizer.val()), symbolTable.indexOf(tokenizer.val()));
			}
			break;
		case KEYWORD:
			switch (tokenizer.keyword()) {
			case TRUE:
				writer.writePush(Segment.CONSTANT, 0);
				writer.writeArithmetic(Command.NOT);
				break;
			case NULL:
			case FALSE:
				writer.writePush(Segment.CONSTANT, 0);
				break;
			case THIS:
				writer.writePush(Segment.POINTER, 0);
				break;
			default:
				throw new Exception("Found invalid keyword in expression");
			}
			break;
		}
	}

	// compile a (possibly empty) comma-separated list of expressions
	private void compileExpressionList() throws Exception {
		// (expression (',' expression)* )?
		if (!tokenizer.peek().equals(")")) {
			compileExpression();
			args++;
			while (!tokenizer.peek().equals(")")) {
				assertSymbol(',');
				compileExpression();
				args++;
			}
		}
	}

}
