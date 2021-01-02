package vm;

import java.io.File;
import java.util.Arrays;

// integrates the Parser and CodeWriter modules, handles walking of the file tree
// (only at depth level 1, does not go into children) when translating directories
public class VirtualMachine {

	// consumes a directory name or .vm file name (extension necessary)
	public static void main(String[] args) throws Exception {
		if (args.length == 0)
			throw new Exception("Must specify file or directory name");

		// if we have a directory, get an array of all .vm files
		String[] toParse;
		Boolean prefix;
		if (args[0].endsWith(".vm")) {
			toParse = new String[1];
			toParse[0] = args[0];
			prefix = false;
		} else {
			toParse = Arrays.stream(new File(args[0]).list()).filter(n -> n.endsWith(".vm")).toArray(String[]::new);
			prefix = true;
		}

		Parser parser;
		String outputName = args[0].endsWith(".vm") ? args[0].substring(0, args[0].length() - 3) : args[0];
		CodeWriter codeWriter = new CodeWriter(outputName);

		for (String fileName : toParse) {
			codeWriter.setFileName(pathToName(fileName));
			parser = new Parser(prefix ? args[0] + "\\" + fileName : fileName);

			while (parser.hasCommand()) {
				parser.advance();
				switch (parser.commandType()) {
				case C_ARITHMETIC:
					codeWriter.writeArithmetic(parser.arg1());
					break;
				case C_PUSH:
				case C_POP:
					codeWriter.writePushPop(parser.commandType(), parser.arg1(), parser.arg2());
					break;
				case C_LABEL:
					codeWriter.writeLabel(parser.arg1());
					break;
				case C_GOTO:
					codeWriter.writeGoto(parser.arg1());
					break;
				case C_IF:
					codeWriter.writeIf(parser.arg1());
					break;
				case C_FUNCTION:
					codeWriter.writeFunction(parser.arg1(), parser.arg2());
					break;
				case C_CALL:
					codeWriter.writeCall(parser.arg1(), parser.arg2());
					break;
				case C_RETURN:
					codeWriter.writeReturn();
					break;
				default:
					throw new Exception("Unknown command type '" + parser.commandType() + '\'');
				}
			}
		}
		codeWriter.close();
	}

	// extracts file name from a path
	public static String pathToName(String path) {
		path = path.substring(0, path.length() - 3);
		path = path.replaceAll("\\\\", "/");
		while (path.contains("/"))
			path = path.substring(path.indexOf("/") + 1, path.length());
		return path;
	}
}
