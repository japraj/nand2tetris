package symboltable;

import java.util.Hashtable;
import java.util.stream.Stream;

// Keep track of all Symbols encountered in Stream so far (and store information about them such as initialization context, type, etc)
public class SymbolTable {

	// one table for each scope; classTable refers to the global namespace, while
	// subroutineTable refers to the local namespace
	private Hashtable<String, Symbol> classTable;
	private Hashtable<String, Symbol> subroutineTable;
	private boolean subroutineOffset;

	public SymbolTable() {
		classTable = new Hashtable<String, Symbol>();
		subroutineTable = new Hashtable<String, Symbol>();
		subroutineOffset = false;
	}

	// clear the current subroutineTable and reset index (used when compiler exits
	// a local scope)
	public void startNewSubroutine(boolean offset) {
		subroutineTable = new Hashtable<String, Symbol>();
		subroutineOffset = offset;
	}

	// add a new symbol to the specified table; the scope of the symbol is
	// determined by its Kind
	public void define(String name, String type, Kind kind) {
		if (kind == Kind.STATIC || kind == Kind.FIELD) {
			classTable.put(name, new Symbol(type, kind, varCount(kind)));
		} else {
			byte offset = (byte) (subroutineOffset && kind == Kind.ARG ? 1 : 0);
			subroutineTable.put(name, new Symbol(type, kind, varCount(kind) + offset));
		}
	}

	// count the number of variables stored in local and global scope with specified
	// Kind
	public int varCount(Kind kind) {
		return (int) Stream.concat(classTable.values().stream(), subroutineTable.values().stream())
				.filter(s -> (s.kind == kind)).count();
	}

	// find a symbol with specified name, or throw an exception if it does not
	// exist/is not stored; note that local scope overrides global scope - if a
	// variable is defined in both, we will always produce the local definition
	private Symbol findSymbol(String symbolName) throws Exception {
		if (subroutineTable.containsKey(symbolName))
			return subroutineTable.get(symbolName);
		else if (classTable.containsKey(symbolName))
			return classTable.get(symbolName);
		else
			throw new Exception("Symbol '" + symbolName + "' DNE");
	}

	// find the Kind of the symbol with specified name (if it DNE/is not stored,
	// produce NONE)
	public Kind kindOf(String symbolName) {
		try {
			return findSymbol(symbolName).kind;
		} catch (Exception e) {
			return Kind.NONE;
		}
	}

	// produce type of specified symbol, or propogate exception if DNE
	public String typeOf(String symbolName) throws Exception {
		return findSymbol(symbolName).type;
	}

	// produce index of specified symbol, or propogate exception if DNE
	public int indexOf(String symbolName) throws Exception {
		return findSymbol(symbolName).index;
	}

}
