# Toy language

I wanted to build a toy language since I've never built one and it seems fun.

## MVP

- [x] numbers
- [x] strings
  - so far pretty much useless
- [ ] booleans
- [x] variables
  - declaration
  - assignment
- [ ] if
- [ ] for
- [x] print
- [ ] functions

## MVP Spec

```
program := statement* EOF

statement := ( declaration | assignment | if | for | print | functionDeclaration ) ';'

declaration := ( identifier ':=' expression | identifier ) ';'
assignment := identifier '=' expression ';'

```
