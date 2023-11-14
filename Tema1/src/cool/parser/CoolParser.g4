parser grammar CoolParser;

options {
    tokenVocab = CoolLexer;
}

@header{
    package cool.parser;
}

program: (class SEMI)+ EOF;

class: CLASS classId=TYPE (INHERITS parentClassId=TYPE)? LBRACE (feature SEMI)* RBRACE;

feature: funcId=ID LPAREN (formals+=formal (COMMA formals+=formal)*)? RPAREN COLON funcType=TYPE LBRACE expr RBRACE
       | varId=ID COLON varType=TYPE (ASSIGN expr);

formal: formalId=ID COLON formalType=TYPE;

expr: name=ID ASSIGN e=expr             # assign
    ;
