package vm;

import java.io.FileWriter;
import java.io.IOException;

// translates VM commands to Hack assembly code, with respect to the
// standard VM mapping. Also handles writing to the output file

// Note: the CodeWriter includes comments containing the original bytecode
// (so we have chunks of ASM separated by comments) to make debugging easier;
// this is not part of the standard
public class CodeWriter {

	private FileWriter writer;
	private StringBuilder out;
	// name of the file we are currently operating on; used in naming of
	// ASM variables
	private String fileName;
	// label accumulator, helps ensure our ternary labels are unique
	// (see writePredicate method for usage)
	private int lastLabel;
	// current function definition we are translating
	private String functionName;

	public CodeWriter(String outputName) throws IOException {
		writer = new FileWriter(outputName + ".asm");
		out = new StringBuilder();
		lastLabel = 0;
		functionName = "f";

		writeInit();
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	private void addLine(String line) {
		out.append(line);
		out.append('\n');
	}

	private void write() throws IOException {
		writer.write(out.toString());
		out.delete(0, out.length());
	}

	// adds bootstrap code to header (initializes the stack pointer and memseg
	// pointers and calls the Sys.init function)
	private void writeInit() throws IOException {
		// *SP = 256
		addLine("@256");
		addLine("D=A");
		addLine("@SP");
		addLine("M=D");
		// add the Sys.init call and call write()
		writeCall("Sys.init", 0);
	}

	// writes asm translation of stack arithmetic commands to output file
	public void writeArithmetic(String command) throws Exception {
		switch (command) {
		case "add":
		case "sub":
		case "and":
		case "or":
			writeBinaryOp(command);
			break;
		case "neg":
		case "not":
			writeUnaryOp(command);
			break;
		case "eq":
		case "lt":
		case "gt":
			writePredicate(command);
			break;
		default:
			throw new Exception("Unrecognized Operation '" + command + '\'');
		}
	}

	// write asm translation of binary operations (add, sub, and, or)
	private void writeBinaryOp(String command) throws IOException {
		char op;
		switch (command) {
		case "add":
			op = '+';
			break;
		case "sub":
			op = '-';
			break;
		case "and":
			op = '&';
			break;
		case "or":
		default:
			op = '|';
			break;
		}
		// add commment w/ command for easy debugging
		addLine("//" + command);
		// SP--
		addLine("@SP");
		addLine("M=M-1");
		// D = *SP
		addLine("A=M");
		addLine("D=M");
		// SP--
		addLine("@SP");
		addLine("M=M-1");
		// M = D op *SP, where op is one of +,-,&,|
		addLine("A=M");
		addLine("M=D" + op + 'M');
		if (command.equals("sub")) // we want to compute *SP - D, not D - SP; all other ops are fine
			addLine("M=-M");
		// SP++
		addLine("@SP");
		addLine("M=M+1");
		write();
	}

	// write asm translation of unary operations (negation, bitwise not)
	private void writeUnaryOp(String command) throws IOException {
		char op;
		switch (command) {
		case "neg":
			op = '-';
			break;
		case "not":
		default:
			op = '!';
			break;
		}
		// add commment w/ command for easy debugging
		addLine("//" + command);
		// SP--
		addLine("@SP");
		addLine("M=M-1");
		// *SP = op *SP, where op is one of -,!
		addLine("A=M");
		addLine("M=" + op + 'M');
		// SP++
		addLine("@SP");
		addLine("M=M+1");
		write();
	}

	// write asm translations of predicates (equal, less than, greater than)
	private void writePredicate(String command) throws IOException {
		String predicate;

		switch (command) {
		case "eq":
			predicate = "EQ";
			break;
		case "lt":
			predicate = "GT";
			break;
		case "gt":
		default:
			predicate = "LT";
			break;
		}

		// add commment w/ command for easy debugging
		addLine("//" + command);
		// SP--
		addLine("@SP");
		addLine("M=M-1");
		// D = *SP
		addLine("A=M");
		addLine("D=M");
		// SP--
		addLine("@SP");
		addLine("M=M-1");
		// D = D - *SP
		addLine("A=M");
		addLine("D=D-M");
		// *SP = predicate(D) ? -1 : 0, we use -1 to represent true, 0 to represent
		// false (in particular, the binary representations)
		addLine("@LBL" + lastLabel + "_0");
		addLine("D;J" + predicate);
		addLine("@SP"); // next 3 lines are *SP = 0
		addLine("A=M");
		addLine("M=0");
		addLine("@LBL" + lastLabel + "_1");
		addLine("0;JMP"); // unconditional jump, skip the -1 assignment
		addLine("(LBL" + lastLabel + "_0)");
		addLine("@SP"); // next 3 lines are *SP = -1
		addLine("A=M");
		addLine("M=-1");
		addLine("(LBL" + lastLabel + "_1)");
		// SP++
		addLine("@SP");
		addLine("M=M+1");
		write();

		// need an accumulator to eliminate goto/label collisions
		lastLabel++;
	}

	// translates commands of the form "push seg i"/"pop seg i"
	// where seg refers to a memory segment (virtual or allocated),
	// and i refers to a particular location/index in that memSeg. The
	// push or pop operation itself is applied to the "stack" w.r.t.
	// the specified memSeg.
	public void writePushPop(Command type, String segment, int index) throws Exception {
		String base = "0";
		switch (segment) {
		case "argument":
			base = "ARG";
			break;
		case "local":
			base = "LCL";
			break;
		case "static":
			base = "16";
			break;
		case "constant":
			base = "0";
			break;
		case "this":
			base = "THIS";
			break;
		case "that":
			base = "THAT";
			break;
		case "pointer":
			base = "3";
			break;
		case "temp":
			base = "5";
			break;
		}
		// common to both push and pop
		// add commment w/ command for easy debugging
		addLine("//" + (type == Command.C_PUSH ? "push" : "pop") + ' ' + segment + ' ' + index);
		// D = BASE + i, a ptr (BASE refers to the base address of a memseg)
		if (!segment.equals("static")) {
			addLine("@" + index);
			addLine("D=A");
			addLine("@" + base);
			try {
				Integer.parseInt(base);
				addLine("D=D+A");
			} catch (Exception e) {
				addLine("D=D+M");
			}
		}

		switch (type) {
		case C_PUSH:
			if (segment.equals("static")) {
				// if static, we want to use a variable
				addLine("@" + fileName + "." + index);
				addLine("D=M");
			} else if (!segment.equals("constant")) {
				// D = *(b + i), only if seg != constant|static
				addLine("A=D");
				addLine("D=M");
			}
			// SP* = D
			addLine("@SP");
			addLine("A=M");
			addLine("M=D");
			// SP++
			addLine("@SP");
			addLine("M=M+1");
			break;
		case C_POP:
		default:
			if (segment.equals("static")) {
				addLine("@" + fileName + "." + index);
				addLine("D=A");
			}
			// *13 = b + i, general purpose register used to store intermediate value
			addLine("@13");
			addLine("M=D");
			// SP--
			addLine("@SP");
			addLine("M=M-1");
			// D = *SP
			addLine("A=M");
			addLine("D=M");
			// *13 = D, which is equivalent to '*(b + i) = *SP' (because of the previous
			// instructions)
			addLine("@13");
			addLine("A=M");
			addLine("M=D");
			break;
		}

		write();
	}

	// note for the 3 fns below: labels' ASM mappings have the form
	// '(functionName$label)'

	// writes a label using above form
	public void writeLabel(String label) throws IOException {
		addLine("//label " + label);
		addLine('(' + functionName + '$' + label + ')');
		write();
	}

	// writes the ASM mapping for a goto statement to the specified label
	public void writeGoto(String label) throws IOException {
		addLine("//goto " + label);
		addLine('@' + functionName + '$' + label);
		addLine("0;JMP");
		write();
	}

	// writes the ASM mapping for an if-goto statement that moves to the
	// address specified by the label if the top-most value in the working stack
	// is not equal to 0 (otherwise, it simply continues in its current line of
	// execution)
	public void writeIf(String label) throws IOException {
		addLine("//if-goto " + label);
		// SP--
		addLine("@SP");
		addLine("M=M-1");
		// D = *SP
		addLine("@SP");
		addLine("A=M");
		addLine("D=M");
		// Conditional Jump - if (!pop()) goto(label);
		addLine('@' + functionName + '$' + label);
		addLine("D;JNE");
		write();
	}

	// saves state of current function (caller) in the stack and initializes a
	// private world for the callee to execute without disruption
	public void writeCall(String functionName, int numArgs) throws IOException {
		// push return-address; (Using the label declared at end)
		addLine("@return" + lastLabel);
		addLine("D=A");
		addLine("@SP");
		addLine("A=M");
		addLine("M=D");
		addLine("@SP");
		addLine("M=M+1");
		// push LCL; Save LCL of the calling function
		addPush("LCL");
		// push ARG; Save ARG of the calling function
		addPush("ARG");
		// push THIS; Save THIS of the calling function
		addPush("THIS");
		// push THAT; Save THAT of the calling function
		addPush("THAT");
		// ARG = SP-n-5; Reposition ARG (n = number of args.)
		addLine("@SP");
		addLine("D=M");
		addLine("@" + (numArgs + 5));
		addLine("D=D-A");
		addLine("@ARG");
		addLine("M=D");
		// LCL = SP; Reposition LCL
		addLine("@SP");
		addLine("D=M");
		addLine("@LCL");
		addLine("M=D");
		// goto f; Transfer control
		addLine("@" + functionName);
		addLine("0;JMP");
		// (return-address); Declare a label for the return-address
		addLine("(return" + lastLabel + ")");
		write();

		lastLabel++;
	}

	// given x, push *x onto the stack
	private void addPush(String ptr) {
		// D = *ptr
		addLine("@" + ptr);
		addLine("D=M");
		// SP* = D
		addLine("@SP");
		addLine("A=M");
		addLine("M=D");
		// SP++
		addLine("@SP");
		addLine("M=M+1");
	}

	// returns control to the caller function and in doing so, restores the
	// state of the global stack to the caller frame using information encoded
	// during the original call
	public void writeReturn() throws IOException {
		addLine("//return");
		// FRAME = LCL; FRAME is a temporary variable (reg 14)
		addLine("@LCL");
		addLine("D=M");
		addLine("@14");
		addLine("M=D");
		// RET = *(FRAME-5); Put the return-address in a temp. var. (reg 15)
		updateWRTFrame("15", 5);
		// *ARG = pop(); Reposition the return value for the caller
		addLine("@SP");
		addLine("M=M-1");
		addLine("@SP");
		addLine("A=M");
		addLine("D=M");
		addLine("@ARG");
		addLine("A=M");
		addLine("M=D");
		// SP = ARG+1; Restore SP of the caller
		addLine("@ARG");
		addLine("D=M+1");
		addLine("@SP");
		addLine("M=D");
		// THAT = *(FRAME-1); Restore THAT of the caller
		updateWRTFrame("THAT", 1);
		// THIS = *(FRAME-2); Restore THIS of the caller
		updateWRTFrame("THIS", 2);
		// ARG = *(FRAME-3); Restore ARG of the caller
		updateWRTFrame("ARG", 3);
		// LCL = *(FRAME-4); Restore LCL of the caller
		updateWRTFrame("LCL", 4);
		// goto RET; Goto return-address (in the caller’s code)
		addLine("@15");
		addLine("A=M");
		addLine("0;JMP");
		write();
	}

	// stores *(FRAME - i) in the specified register (WRT = with respect to)
	// CONSTRAINT: FRAME must be stored in register 13 prior to calling this
	private void updateWRTFrame(String dest, int i) {
		addLine("@14");
		addLine("D=M");
		addLine("@" + i);
		addLine("A=D-A");
		addLine("D=M");
		addLine("@" + dest);
		addLine("M=D");
	}

	// declare a label for the function entry (of the form '(functionName)') and
	// initialize numLocals entries with a value of 0 for the local variables
	public void writeFunction(String functionName, int numLocals) throws IOException {
		this.functionName = functionName;
		addLine("//function " + functionName + ' ' + numLocals);
		// (f)
		addLine('(' + functionName + ')');
		// init local vars
		for (int i = numLocals; i > 0; i--) {
			// D = 0
			addLine("@0");
			addLine("D=A");
			// SP* = D
			addLine("@SP");
			addLine("A=M");
			addLine("M=D");
			// SP++
			addLine("@SP");
			addLine("M=M+1");
		}
		write();
	}

	public void close() throws IOException {
		writer.close();
	}
}
