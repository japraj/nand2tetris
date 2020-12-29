package assembler;

// translate hack assembly C-command field mneumonics to binary codes
public class Code {

	private static char isPresent(String field, String toCheck) {
		return field.contains(toCheck) ? '1' : '0';
	}

	// produces 3 bits from a dest mneunomic
	public static String parseDest(String field) {
		char A = isPresent(field, "A");
		char D = isPresent(field, "D");
		char M = isPresent(field, "M");
		return new StringBuilder().append(A).append(D).append(M).toString();
	}

	// produces 7 bits from a comp mneunomic of the form a ccc ccc
	public static String parseComp(String field) {
		char a = isPresent(field, "M");
		String cccccc = "";

		// the a bit determines whether we operate on M or A, but the
		// ccc ccc bits are the same for both, so the replace call below
		// means we only need to check for A in the switch statement below
		field = field.replace("M", "A");

		switch (field) {
		case "0":
			cccccc = "101010";
			break;
		case "1":
			cccccc = "111111";
			break;
		case "-1":
			cccccc = "111010";
			break;
		case "D":
			cccccc = "001100";
			break;
		case "A":
			cccccc = "110000";
			break;
		case "!D":
			cccccc = "001101";
			break;
		case "!A":
			cccccc = "110001";
			break;
		case "-D":
			cccccc = "001111";
			break;
		case "-A":
			cccccc = "110011";
			break;
		case "D+1":
			cccccc = "011111";
			break;
		case "A+1":
			cccccc = "110111";
			break;
		case "D-1":
			cccccc = "001110";
			break;
		case "A-1":
			cccccc = "110010";
			break;
		case "D+A":
			cccccc = "000010";
			break;
		case "D-A":
			cccccc = "010011";
			break;
		case "A-D":
			cccccc = "000111";
			break;
		case "D&A":
			cccccc = "000000";
			break;
		case "D|A":
			cccccc = "010101";
			break;
		}
		return a + cccccc;
	}

	// produces 3 bits from a jump mneunomic
	public static String parseJump(String field) {
		String jjj = "";
		switch (field) {
		case "":
			jjj = "000";
			break;
		case "JGT":
			jjj = "001";
			break;
		case "JEQ":
			jjj = "010";
			break;
		case "JGE":
			jjj = "011";
			break;
		case "JLT":
			jjj = "100";
			break;
		case "JNE":
			jjj = "101";
			break;
		case "JLE":
			jjj = "110";
			break;
		case "JMP":
			jjj = "111";
			break;
		}
		return jjj;
	}

	// produce 15 bit mapping of int (consumed in string format)
	// CONSTRAINT: given decimal int should not need more than 15 chars to be
	// represented in binary
	public static String mapDecimalToBinary(int decimal) {
		String binary = Integer.toBinaryString(decimal);
		while (binary.length() < 15)
			binary = "0" + binary;
		return binary;
	}

}
