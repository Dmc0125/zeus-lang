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
- [ ] for
- [x] print
- [ ] functions

## Grammar

```
program := statement*

statement := variableDeclaration ';'
           | variableAssignment ';'
           | if ';'
           | print ';'
           | block

variableDeclaration := ident ':=' expression
                     | ident ':' type
                     | ident ':' type '=' expression
variableAssignment  := ident '=' expression
print               := ('print'  | 'println') expression
if                  := 'if' expression '{' statement* '}' ('else' if)* ('else' block)?
block               := '{' statement* '}'

expression := '(' logical ')' | logical
logical    := comparison (('&&' | '||') comparison)*
comparison := factor (('==' | '!=' | '<' | '>' | '<=' | '>=') factor)*
factor     := term (('+' | '-') term)*
term       := unary (('*' | '/') unary)*
unary      := (('+' | '-' | '!') unary) | literal
literal    := number
            | string
            | bool
            | ident
```
