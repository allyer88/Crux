grammar Crux;
program
 : declList EOF
 ;

declList
 : decl*
 ;

decl
 : varDecl
 | arrayDecl
 | functionDefn
 ;
 literal
  : Integer
  | True
  | False
  ;

//Example) int a;
varDecl
 : type Identifier ';'
 ;

type
  : Identifier
  ;

//Example) int arr[10];
arrayDecl
 : type Identifier OpenBracket literal CloseBracket SemiColon
 ;

//Example) parameters add(int a, int b)
param
 : type Identifier
 ;
paramList
 : (param (Comma param)*)?
 ;
//Example void foo(){} | void foo(int a){}
functionDefn
 : type Identifier OpenParen paramList CloseParen stmtBlock
 ;

//Example) a = 10;
assignStmt
 : designator Assign expr0 SemiColon
 ;

//Example) Add() | Add(1,2) | Add(1,2,3,4,5)
callExpr
 : Identifier OpenParen exprList CloseParen
 ;
exprList
 : (expr0 (Comma expr0)*)?
 ;
callStmt
 : callExpr SemiColon
 ;
ifStmt
 : If expr0 stmtBlock (Else stmtBlock)?
 ;

loopStmt
 : Loop stmtBlock
 ;
breakStmt
 : Break SemiColon
 ;
continueStmt
 : Continue SemiColon
 ;
returnStmt
 : Return expr0 SemiColon
 ;
stmt
 : varDecl
 | callStmt
 | assignStmt
 | ifStmt
 | loopStmt
 | breakStmt
 | continueStmt
 | returnStmt
 ;

stmtList : stmt*;

//{...}
stmtBlock
 : OpenBrace stmtList CloseBrace
 ;

//example) arr[i] or arr
designator
 : Identifier(OpenBracket expr0 CloseBracket)?
 ;

//">=" | "<=" | "!=" | "==" | ">" | "<"
op0
 : GreaterEqual
 | LesserEqual
 | NotEqual
 | Equal
 | GreaterThan
 | LessThan
 ;

//"+" | "-" | "||"
op1
 : Add
 | Sub
 | Or
 ;

//"*" | "/" | "&&"
op2
 : Mul
 | Div
 | And
 ;

expr0
 : expr1(op0 expr1)?
 ;
expr1
 : expr2
 | expr1 op1 expr2;
expr2
 : expr3
 | expr2 op2 expr3;
expr3
 : Not expr3
 | OpenParen expr0 CloseParen
 | designator
 | callExpr
 | literal;

Integer
 : '0'
 | [1-9] [0-9]*
 ;

Identifier
 : [a-zA-Z] [a-zA-Z0-9_]*
 ;

WhiteSpaces
 : [ \t\r\n]+ -> skip //be ignored by the scanner
 ;

Comment
 : '//' ~[\r\n]* -> skip //be ignored by the scanner
 ;

 //reserved keywords
 And: '&&';
 Or: '||';
 Not: '!';
 If: 'if';
 Else: 'else';
 For: 'for';
 Break: 'break';
 True: 'true';
 False: 'false';
 Return: 'return';
 Continue: 'continue';
 Loop: 'loop';
 //some characters
 OpenParen: '(';
 CloseParen: ')';
 OpenBrace: '{';
 CloseBrace: '}';
 OpenBracket: '[';
 CloseBracket: ']';
 Add: '+';
 Sub: '-';
 Mul: '*';
 Div: '/';
 GreaterEqual: '>=';
 LesserEqual: '<=';
 NotEqual: '!=';
 Equal: '==';
 GreaterThan: '>';
 LessThan: '<';
 Assign: '=';
 Comma: ',';
 SemiColon: ';';


