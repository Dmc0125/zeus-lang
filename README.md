# Toy language

I wanted to build a toy language since I've never built one and it seems fun.

## MVP

- [ ] numbers
- [ ] strings
- [ ] booleans
- [ ] variables
    - declaration
    - assignment
- [ ] if
- [ ] for
- [ ] print
- [ ] functions

## MVP Spec

```
program := statement* EOF

statement := ( declaration | assignment | if | for | print | functionDeclaration ) ';'

declaration := ( identifier ':=' expression | identifier ) ';'
assignment := identifier '=' expression ';'

```

