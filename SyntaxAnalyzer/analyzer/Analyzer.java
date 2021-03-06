package analyzer;

import java.io.File;
import java.util.Arrays;

// Invokes the Parser module, and manages command line interfacing
public class Analyzer {

	// consumes a directory name or .jack file name (extension necessary)
	public static void main(String[] args) throws Exception {
		if (args.length == 0)
			throw new Exception("Must specify file or directory name");

		// if we have a directory, get an array of all .jack files
		String[] toParse;
		Boolean prefix;
		if (args[0].endsWith(".jack")) {
			toParse = new String[1];
			toParse[0] = args[0];
			prefix = false;
		} else {
			toParse = Arrays.stream(new File(args[0]).list()).filter(n -> n.endsWith(".jack")).toArray(String[]::new);
			prefix = true;
		}

		Parser parser;

		for (String fileName : toParse) {
			parser = new Parser(prefix ? args[0] + "\\" + fileName : fileName);
			try {
				parser.compileClass();
				parser.close();
			} catch (Exception e) {
				parser.close();
				throw e;
			}
			// parser.compileClass();
			// parser.close();
		}
	}
}
