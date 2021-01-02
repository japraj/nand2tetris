package vm;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.Scanner;

// opens and parses VM files, removing comments/whitespace, and breaking
// each line into its component fields. Provides an API for working with 
// a VM file line by line (see public methods)
// CONSTRAINT: assumes the file is well-formed (no syntactical errors)
public class Parser {

	// array of cmds to be parsed
	private LinkedList<String> commands;
	// current command
	private String command;

	// populate commands array by splitting file on line breaks.
	// Also removes all comments and whitespace
	public Parser(String fileName) throws FileNotFoundException {
		commands = new LinkedList<String>();
		Scanner reader = new Scanner(new File(fileName));
		String line;
		while (reader.hasNextLine()) {
			line = reader.nextLine().trim();
			if (line.contains("//"))
				line = line.substring(0, line.indexOf("//"));
			if (!line.isEmpty())
				commands.addLast(line);
		}
		reader.close();
	}

	// produce status of internal commands array
	public Boolean hasCommand() {
		return commands.size() > 0;
	}

	// read and parse next command from stream, updating fields
	// in the process
	// CONSTRAINT: only called when hasCommand() == true
	public void advance() {
		command = commands.poll();
	}

	// produce type of current command
	public Command commandType() {
		switch (command.split("\\s+")[0]) {
		case "push":
			return Command.C_PUSH;
		case "pop":
			return Command.C_POP;
		case "label":
			return Command.C_LABEL;
		case "goto":
			return Command.C_GOTO;
		case "if-goto":
			return Command.C_IF;
		case "function":
			return Command.C_FUNCTION;
		case "call":
			return Command.C_CALL;
		case "return":
			return Command.C_RETURN;
		default:
			return Command.C_ARITHMETIC;
		}
	}

	// returns the first argument of the current command; for ARITHMETIC, we just
	// return the command itself.
	// CONSTRAINT: never called when command is RETURN
	public String arg1() {
		return command.split("\\s+")[commandType() == Command.C_ARITHMETIC ? 0 : 1];
	}

	// returns second arg of current command
	// CONSTRAINT: only called when current command is PUSH, POP, FUNCTION, or CALL
	public int arg2() {
		return Integer.parseInt(command.split("\\s+")[2]);
	}
}
