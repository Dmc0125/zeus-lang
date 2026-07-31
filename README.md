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
- [ ] if
- [ ] for
- [x] print
- [ ] functions

## Grammar

```
program := statement*

statement := (variableDeclaration ';')
           | (variableAssignment ';')
           | (print ';')

variableDeclaration := ident ':=' expression
                     | ident ':' type
                     | ident ':' type '=' expression
variableAssignment  := ident '=' expression

print := ('print'  | 'println') expression

expression := '(' factor ')' | factor
factor     := term (('+' | '-') term)*
term       := unary (('*' | '/') unary)*
unary      := (('+' | '-' | '!') unary) | literal
literal    := number
            | string
            | bool
            | ident
```
