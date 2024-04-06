grammar Crux;
program
 : declList EOF
 ;

declList
 : decl*
 ;

decl
 : varDecl
// | arrayDecl
// | functionDefn
 ;

varDecl
 : type Identifier ';'
 ;

type
 : Identifier
 ;

literal
 : Integer
 | True
 | False
 ;

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
 //some characters
 OpenParen: '(';
 Close_paren: ')';
 OpenBrace: '{';
 CloseBrace: '}';
 OpenBracket: '[';
 CoseBracket: ']';
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


