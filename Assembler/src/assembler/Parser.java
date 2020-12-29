package assembler;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.Scanner;

// parses ASM files according to the hack machine language specification
// breaks down each line into its component fields and provides an API 
// for working with an ASM file line by line (see public methods)
// CONSTRAINT: assumes the file is well-formed (no syntactical errors)
public class Parser {

	// array of cmds to be parsed
	private LinkedList<String> commands;
	// below is used to store non-label cmds during initial enumeration
	private LinkedList<String> temp;
	// current command
	private String command;

	// populate commands array by splitting file on line breaks.
	// Also removes all comments and whitespace
	public Parser(String fileName) throws FileNotFoundException {
		commands = new LinkedList<String>();
		temp = new LinkedList<String>();
		Scanner reader = new Scanner(new File(fileName));
		String line;
		while (reader.hasNextLine()) {
			line = reader.nextLine().replaceAll("\\s+", "");
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

	// place current command back into worklist
	// CONSTRAINT: only allowed to call this if advanced() has been called at least
	// once.
	public void retain() {
		temp.addLast(command);
	}

	// used to "merge" temp/commands after initial enumeration
	public void allocate() {
		commands = temp;
		temp = null;
	}

	// produce type of current command
	public Command commandType() throws Exception {
		if (command == null)
			throw new Exception("Command not initialized");

		switch (command.charAt(0)) {
		case '(':
			return Command.L_COMMAND;
		case '@':
			return Command.A_COMMAND;
		default:
			return Command.C_COMMAND;
		}
	}

	// select symbol of current command - the xxx in '@xxx' for A or '(xxx)' for L
	// CONSTRAINT: current command must be either of type Label or Address (L/A)
	public String symbol() {
		// we want to remove the first char of A and L instr. and for L instr, remove
		// last char too
		return command.substring(1, command.length() - ((command.charAt(0) == '(') ? 1 : 0));
	}

	// dest=comp;jump

	// select DEST field of current command
	// CONSTRAINT: current command must be a C_COMMAND
	public String dest() {
		return command.contains("=") ? command.substring(0, command.indexOf('=')) : "";
	}

	// select COMP field of current command
	// CONSTRAINT: current command must be a C_COMMAND
	public String comp() {
		String destRemoved = (command.contains("=")) ? command.substring(command.indexOf("=") + 1) : command;
		return destRemoved.contains(";") ? destRemoved.substring(0, destRemoved.indexOf(";")) : destRemoved;
	}

	// select JUMP field of current command
	// CONSTRAINT: current command must be a C_COMMAND
	public String jump() {
		return command.contains(";") ? command.substring(command.indexOf(";") + 1) : "";
	}
}
