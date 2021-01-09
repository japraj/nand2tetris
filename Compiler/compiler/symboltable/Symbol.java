package symboltable;

public class Symbol {

	public String type;
	public Kind kind;
	public int index;

	public Symbol(String type, Kind kind, int index) {
		this.type = type;
		this.kind = kind;
		this.index = index;
	}
	
}
