# Toy language

I wanted to build a toy language since I've never built one and it seems fun.

## MVP

- [x] numbers
- [x] strings
  - so far pretty much useless
- [x] booleans
- [x] variables
  - declaration
  - assignment
- [x] if
- [x] for
- [x] print
- [x] functions

## Grammar

```
program := statement*

statement := variableDeclaration ';'
           | variableAssignment ';'
           | print ';'
           | block
           | if
           | for
           | call ';'
           | 'break' ';'
           | 'continue' ';'
           | 'return' expression? ';'


variableDeclaration := ident ':=' expression
                     | ident ':' type
                     | ident ':' type '=' expression
variableAssignment  := ident '=' expression
print               := ('print'  | 'println') expression
type                := 'number' | 'string' | 'bool'
functionDeclaration := 'fun' ident '(' parameter* ')' (':' type)? block

block               := '{' statement* '}'
if                  := 'if' expression block ('else' if)* ('else' block)?
for                 := 'for' expression block
                     | 'for' variableDeclaration? ';' expression? ';' variableAssignment? block

expression := comparison (('&&' | '||') comparison)*
comparison := factor (('==' | '!=' | '<' | '>' | '<=' | '>=') factor)*
factor     := term (('+' | '-') term)*
term       := unary (('*' | '/') unary)*
unary      := (('+' | '-' | '!') unary) | primary
primary    := number
            | string
            | bool
            | ident
            | '(' expression ')'
            | call

call := ident '(' expression* ')'
```
