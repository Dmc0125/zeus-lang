# Zeus language

I wanted to build a toy language since I've never built one and it seems fun. This is project is partly inspired by the book [Crafting Interpreters](https://craftinginterpreters.com/), which I read some time ago and although I did not use it as a reference, I figured it would be nice to mention it.

## Tree walk intepreter

The kotlin part of the project is a tree walk interpreter for the language. I never really used Kotlin so I figured this might be a good opportunity to try it. The tree walk interpreter is implemented in 4 steps:

1. Tokenizer - parse the input into tokens
2. Parser - parse the tokens into an AST
3. Analyzer - statically analyze the AST (type checking, etc.)
4. Interpreter - execute the AST

### Grammar

The program consists of single file which is directly executed. It does not require main function as an entry point. It supports basic arithmetic, control flow, function calls and recursion. Supported types are `number`, `string`, `bool`, although string is mostly useless as no string operations are supported.

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

### Example

```
// fibonacci
fun fib(n: number): number {
    if n < 2 {
        return n;
    }
    return fib(n - 1) + fib(n - 2);
}

println fib(30);
```

## VM in C

The next step is to write a compiler and a VM for the language in C.

The c implementation currently supports arithmetic expressions, so it's a small calculator.
