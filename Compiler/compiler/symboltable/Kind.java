package symboltable;

public enum Kind {
	STATIC, FIELD, ARG, VAR, NONE
	// NONE is only used when an identifier is not found in either scope
}
