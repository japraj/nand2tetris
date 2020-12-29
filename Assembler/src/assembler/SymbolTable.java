package assembler;

import java.util.HashMap;

// a symbol : address table; assigns ptrs to intsruction memory for labels, and 
// allocates a register in data memory for variables
public class SymbolTable {

	// note: addresses are stored in binary as strings
	private HashMap<String, String> table;
	private int ramAddress;

	public SymbolTable() {
		table = new HashMap<String, String>();

		// pre-defined symbols:
		this.addEntry("SP", 0);
		this.addEntry("LCL", 1);
		this.addEntry("ARG", 2);
		this.addEntry("THIS", 3);
		this.addEntry("THAT", 4);
		this.addEntry("SCREEN", 16384);
		this.addEntry("KBD", 24576);
		for (int i = 0; i < 16; i++)
			this.addEntry("R" + Integer.toString(i), i);
		ramAddress = 16; // this is the base address for our auto-allocation of symbols
	}

	// addEntry has two definitions - one w/ address, one without; the first
	// automatically assigns addresses (to data memory/RAM) while second takes
	// addresses into instruction memory/ROM (for labels)
	public void addEntry(String symbol) {
		addEntry(symbol, ramAddress);
		ramAddress++;
	}

	// address should be in decimal; we map to binary internally
	public void addEntry(String symbol, int address) {
		table.put(symbol, Code.mapDecimalToBinary(address));
	}

	public Boolean contains(String symbol) {
		return table.containsKey(symbol);
	}

	public String getAddress(String symbol) {
		return table.get(symbol);
	}
}
