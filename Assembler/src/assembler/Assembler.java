package assembler;

import java.io.FileWriter;

// handles file i/o and integrates the parser, code, and symbolTable modules
public class Assembler {

	// note: consumes filename (without extension) as command line argument
	public static void main(String[] args) throws Exception {
		if (args.length == 0)
			throw new Exception("Must specify file name");
		Parser parser = new Parser(args[0] + ".asm");
		SymbolTable table = new SymbolTable();
		int lineNumber = 0; // line number in instruction memory

		// enumerate all label symbols (so any references to a label in address commands
		// can be easily mapped to an address in instruction memory, even if the
		// reference appears before the declaration)
		while (parser.hasCommand()) {
			parser.advance();
			if (parser.commandType() == Command.L_COMMAND) {
				table.addEntry(parser.symbol(), lineNumber);
			} else {
				parser.retain();
				lineNumber++;
			}
		}
		parser.allocate();

		FileWriter out = new FileWriter(args[0] + ".hack");
		String translation = "";
		String pcomp, pdest, pjump, symbol;
		Command type;

		while (parser.hasCommand()) {
			parser.advance();
			type = parser.commandType();
			if (type == Command.C_COMMAND) {
				pcomp = Code.parseComp(parser.comp());
				pdest = Code.parseDest(parser.dest());
				pjump = Code.parseJump(parser.jump());
				translation = "111" + pcomp + pdest + pjump;
			} else if (type == Command.A_COMMAND) {
				translation = "0";
				symbol = parser.symbol();

				try {
					translation += Code.mapDecimalToBinary(Integer.parseInt(parser.symbol()));
				} catch (NumberFormatException ne) {
					if (!table.contains(symbol))
						table.addEntry(symbol);
					translation += table.getAddress(symbol);
				}
			}
			out.write(translation + '\n');
		}
		out.close();
	}
}
