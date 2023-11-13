lexer grammar CoolLexer;

tokens { ERROR } 

@header{
    package cool.lexer;	
}

@members{    
    private void raiseError(String msg) {
        setText(msg);
        setType(ERROR);
    }

    private void checkString(String str) {
        char[] initChars = str.toCharArray();
        String finalString = "";
        for (int i = 1; i < initChars.length - 1; i++) {
            if (initChars[i] == '\\') {
                if (i + 1 < initChars.length) {
                    switch (initChars[i + 1]) {
                        case 'n': finalString += "\n";
                                  break;
                        case 't': finalString += "\t";
                                  break;
                        case 'b': finalString += "\b";
                                  break;
                        case 'f': finalString += "\f";
                                  break;
                        default: finalString += initChars[i + 1];
                    }
                    i++;
                }
            } else {
                finalString += initChars[i];
            }
        }
        if (finalString.length() > 1024)
            raiseError(getSourceName() + ", line " + getLine() + ":" + getCharPositionInLine() + ", Lexical error: String constant too long");
        else
            setText(finalString);
    }
}

WS: [ \n\f\r\t]+ -> skip;

// Keywords
IF: 'if';
THEN: 'then';
ELSE: 'else';
FI: 'fi';
CLASS: 'class';
INHERITS: 'inherits';
WHILE: 'while';
LOOP: 'loop';
POOL: 'pool';
CASE: 'case';
OF: 'of';
ESAC: 'esac';
LET: 'let';
IN: 'in';
NEW: 'new';
NOT: 'not';
BOOL: 'true' | 'false';
ISVOID: 'isvoid';

// Integers and identifiers
fragment LETTER: [a-zA-Z];
fragment DIGIT: [0-9];
TYPE: [A-Z](LETTER | DIGIT | '_')*;
ID: [a-z](LETTER | DIGIT | '_')*;
INTEGER: DIGIT+;

// String
fragment NEWLINE: '\r'? '\n';
STRING: '"'('\\"' | ('\\' NEWLINE) | ~'\u0000')*? ( '"' { checkString(getText()); }
                                          | NEWLINE { raiseError(getSourceName() + ", line " + getLine() + ":" + getCharPositionInLine() + ", Lexical error: Unterminated string constant"); }
                                          | EOF { raiseError(getSourceName() + ", line " + getLine() + ":" + getCharPositionInLine() + ", Lexical error: EOF in string constant"); });
STRING_NULL: '"'('\u0000' | '\\"' | ('\\' NEWLINE) | .)*? ( '"' { raiseError(getSourceName() + ", line " + getLine() + ":" + getCharPositionInLine() + ", Lexical error: String contains null character"); }
                                                  | NEWLINE { raiseError(getSourceName() + ", line " + getLine() + ":" + getCharPositionInLine() + ", Lexical error: Unterminated string constant"); }
                                                  | EOF { raiseError(getSourceName() + ", line " + getLine() + ":" + getCharPositionInLine() + ", Lexical error: EOF in string constant"); });

// Other symbols
COLON: ':';
SEMI : ';';
COMMA : ',';
ASSIGN : '=';
LPAREN : '(';
RPAREN : ')';
LBRACE : '{';
RBRACE : '}';
PLUS : '+';
MINUS : '-';
MULT : '*';
DIV : '/';
EQUAL : '==';
LT : '<';
LE : '<=';