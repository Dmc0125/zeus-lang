#ifndef MAIN_H
#define MAIN_H

#include "vec.h"
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

typedef int8_t i8;
typedef int16_t i16;
typedef int32_t i32;
typedef int64_t i64;

typedef float f32;
typedef double f64;

typedef enum {
  ErrorNone,
  ErrorUnexpectedToken,
  ErrorNumberOutOfRange,
  ErrorInvalidNumber,
  ErrorUnexpectedEOF,
  ErrorExpectedExpression,
  ErrorUnexpectedOperator,
  ErrorInvalidExpression,

  // VM
  ErrorVMStackUnderflow,
} ErrorType;

typedef struct {
  i32 line;
  i32 col;
  ErrorType type;
} Error;

Error Error_new(i32 line, i32 col, ErrorType type);

void Error_print(Error *error, char *source);

typedef enum {
  TokenNone,

  TokenLitNumber,

  TokenPlus,
  TokenMinus,
  TokenStar,
  TokenSlash,
} TokenType;

typedef struct {
  TokenType type;
  i32 line;
  i32 col;
  size_t lit_idx;
} Token;

DEFINE_VEC(Token, Tokens)

typedef enum {
  PrecedenceNone,

  PrecedencePrimary,

  PrecedenceTerm,   // + -
  PrecedenceFactor, // * /

  PrecedenceUnary,
} Precedence;

typedef enum {
  ExprTypeLitNumber,
  ExprTypeUnary,
  ExprTypeBinary,
} ExprType;

typedef struct Expr Expr;

struct Expr {
  ExprType type;
  i32 line;
  i32 col;

  union {
    // number
    struct {
      f64 value;
    } number;
    // unary
    struct {
      Expr *operand;
      TokenType op;
    } unary;
    // binary
    struct {
      Expr *left;
      Expr *right;
      TokenType op;
    } binary;
  } as;
};

DEFINE_VEC(Expr, Expressions)

typedef struct {
  char *source;

  Token previous;
  Token current;
  Tokens *tokens;
  size_t tokens_idx;

  Expressions *expr_arena;
  Expr *last_expr;
} ParserState;

void ParserState_init(ParserState *state, char *source, Tokens *tokens,
                      Expressions *expr_arena);

Error ParserState_advance(ParserState *state);

Expr *ParserState_alloc_expr(ParserState *state);

typedef Error PrefixFn(ParserState *state, Expr *out);
typedef Error InfixFn(ParserState *state, Expr *left, Expr *out);

typedef struct {
  Precedence precedence;
  PrefixFn *prefix;
  InfixFn *infix;
} PrecedenceRule;

Error parse_lit_number(ParserState *state, Expr *out);

Error parse_unary(ParserState *state, Expr *out);

Error parse_binary(ParserState *state, Expr *left, Expr *out);

Error parse_precedence(ParserState *state, Precedence precedence);

#endif
