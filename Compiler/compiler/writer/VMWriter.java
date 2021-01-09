package writer;

import java.io.FileWriter;
import java.io.IOException;

// handle opening of, and writing of VM commands to, the Output file (in particular, provides an API for writing all VM commands)
public class VMWriter {

	private FileWriter writer;

	// consumes name of file to parse (should have .jack extension)
	public VMWriter(String fileName) throws IOException {
		writer = new FileWriter(fileName.substring(0, fileName.length() - 4) + "vm");
	}

	private void writeLine(String line) throws IOException {
		writer.write(line + '\n');
	}

	// write a VM push command
	public void writePush(Segment seg, int index) throws IOException {
		writeLine("push " + seg.toString().toLowerCase() + ' ' + index);
	}

	// write a VM pop command
	public void writePop(Segment seg, int index) throws IOException {
		writeLine("pop " + seg.toString().toLowerCase() + ' ' + index);
	}

	// write a VM arithmetic command
	public void writeArithmetic(Command command) throws IOException {
		writeLine(command.toString().toLowerCase());
	}

	// write a VM label command
	public void writeLabel(String label) throws IOException {
		writeLine("label " + label);
	}

	// write a VM goto command
	public void writeGoto(String label) throws IOException {
		writeLine("goto " + label);
	}

	// write a VM if-goto command
	public void writeIf(String label) throws IOException {
		writeLine("if-goto " + label);
	}

	// write a VM call command
	public void writeCall(String name, int numArgs) throws IOException {
		writeLine("call " + name + ' ' + numArgs);
	}

	// write a VM function command
	public void writeFunction(String name, int numLocalVars) throws IOException {
		writeLine("function " + name + ' ' + numLocalVars);
	}

	// write a VM return command
	public void writeReturn() throws IOException {
		writeLine("return");
	}

	public void close() throws IOException {
		writer.close();
	}
}
