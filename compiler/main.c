#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

#include "main.h"
#include "vec.h"
#include "vm.h"

// 1. tokenizer
// 2. parse into AST
// 3. analyze AST into IR
// 4. compile IR
// 5. run compiled with VM

Error Error_new(i32 line, i32 col, ErrorType type) {
  return (Error){line, col, type};
}

void Error_print(Error *error, char *source) {
  switch (error->type) {
  case ErrorNone:
    return;
  case ErrorUnexpectedToken:
    printf("Unexpected token at line %d, col %d\n", error->line, error->col);
    break;
  case ErrorNumberOutOfRange:
    printf("Number out of range at line %d, col %d\n", error->line, error->col);
    break;
  case ErrorInvalidNumber:
    printf("Invalid number at line %d, col %d\n", error->line, error->col);
    break;
  case ErrorUnexpectedEOF:
    printf("Unexpected end of file at line %d, col %d\n", error->line,
           error->col);
    break;
  case ErrorExpectedExpression:
    printf("Expected expression at line %d, col %d\n", error->line, error->col);
    break;
  case ErrorUnexpectedOperator:
    printf("Unexpected operator at line %d, col %d\n", error->line, error->col);
    break;
  case ErrorInvalidExpression:
    printf("Invalid expression at line %d, col %d\n", error->line, error->col);
    break;
  case ErrorVMStackUnderflow:
    printf("Stack underflow at line %d, col %d\n", error->line, error->col);
    break;
  }
}

DEFINE_VEC_IMPL(Token, Tokens)

bool is_digit(char c) { return c >= '0' && c <= '9'; }

ErrorType parse_double(char *source, f64 *out) {
  errno = 0;
  char *endptr = NULL;
  f64 _out = strtod(source, &endptr);
  if (out != NULL) {
    *out = _out;
  }

  if (errno == ERANGE) {
    return ErrorNumberOutOfRange;
  }
  if (endptr == source || endptr == NULL || errno != 0) {
    return ErrorInvalidNumber;
  }

  return ErrorNone;
}

Error tokenize(Tokens *tokens, char *str) {
  i32 line = 1;
  i32 col = 1;
  size_t i = 0;

  while (1) {
    char c = str[i];

    if (c == '\0') {
      break;
    }

    if (c == '\n') {
      line += 1;
      col = 1;
      i += 1;
      continue;
    }

    switch (c) {
    case ' ':
    case '\t':
      col += 1;
      i += 1;
      continue;
    case '+':
      Tokens_add(tokens, (Token){TokenPlus, line, col, 0});
      break;
    case '-':
      Tokens_add(tokens, (Token){TokenMinus, line, col, 0});
      break;
    case '*':
      Tokens_add(tokens, (Token){TokenStar, line, col, 0});
      break;
    case '/':
      Tokens_add(tokens, (Token){TokenSlash, line, col, 0});
      break;
    default:
      if (is_digit(c)) {
        i32 start_col = col;
        size_t start_idx = i;

        while (is_digit(str[i]) || str[i] == '.') {
          col += 1;
          i += 1;
        }

        ErrorType parse_err = parse_double(str + start_idx, NULL);
        if (parse_err != ErrorNone) {
          return Error_new(line, start_col, parse_err);
        }

        Tokens_add(tokens, (Token){TokenLitNumber, line, start_col, start_idx});
        continue;
      }

      return Error_new(line, col, ErrorUnexpectedToken);
    }

    col += 1;
    i += 1;
  }

  return (Error){0};
}
void Expr_print(Expr *expr) {
  switch (expr->type) {
  case ExprTypeLitNumber:
    printf("Number(%.02f)", expr->as.number.value);
    break;
  case ExprTypeUnary:
    printf("Unary(%d ", expr->as.unary.op);
    Expr_print(expr->as.unary.operand);
    printf(")");
    break;
  case ExprTypeBinary:
    printf("Binary(");
    Expr_print(expr->as.binary.left);
    printf(" %d ", expr->as.binary.op);
    Expr_print(expr->as.binary.right);
    printf(")");
    break;
  }
}

DEFINE_VEC_IMPL(Expr, Expressions)

void ParserState_init(ParserState *state, char *source, Tokens *tokens,
                      Expressions *expr_arena) {
  state->source = source;
  state->previous = (Token){0};
  state->current = tokens->items[0];
  state->tokens = tokens;
  state->tokens_idx = 0;
  state->expr_arena = expr_arena;
}

Error ParserState_advance(ParserState *state) {
  size_t idx = state->tokens_idx;
  Tokens *tokens = state->tokens;

  if (state->tokens_idx >= tokens->count) {
    return Error_new(0, 0, ErrorUnexpectedEOF);
  }

  if (idx + 1 < tokens->count) {
    state->current = tokens->items[idx + 1];
  } else {
    state->current = (Token){0};
  }

  state->previous = tokens->items[idx];
  state->tokens_idx += 1;

  return (Error){0};
}

Expr *ParserState_alloc_expr(ParserState *state) {
  Expressions_add(state->expr_arena, (Expr){0});
  Expr *expr = &state->expr_arena->items[state->expr_arena->count - 1];
  return expr;
}

PrecedenceRule precedence_rules[] = {
    {0},                                         // None
    {PrecedencePrimary, parse_lit_number, NULL}, // Number
    {PrecedenceTerm, parse_unary, parse_binary}, // Plus
    {PrecedenceTerm, parse_unary, parse_binary}, // Minus
    {PrecedenceFactor, NULL, parse_binary},      // Star
    {PrecedenceFactor, NULL, parse_binary},      // Slash
};

Error parse_lit_number(ParserState *state, Expr *out) {
  Token current = state->previous;
  if (current.type != TokenLitNumber) {
    return Error_new(current.line, current.col, ErrorUnexpectedToken);
  }

  out->type = ExprTypeLitNumber;
  out->line = current.line;
  out->col = current.col;

  ErrorType err =
      parse_double(state->source + current.lit_idx, &out->as.number.value);
  if (err != ErrorNone) {
    return Error_new(current.line, current.col, err);
  }

  return (Error){0};
}

Error parse_unary(ParserState *state, Expr *out) {
  Token op = state->previous;

  switch (op.type) {
  case TokenPlus:
  case TokenMinus:
    break;
  default:
    return Error_new(op.line, op.col, ErrorUnexpectedToken);
  }

  PrecedenceRule rule = precedence_rules[op.type];

  Error err = parse_precedence(state, PrecedenceUnary);
  if (err.type != ErrorNone) {
    return err;
  }

  out->type = ExprTypeUnary;
  out->line = op.line;
  out->col = op.col;

  out->as.unary.op = op.type;
  out->as.unary.operand = state->last_expr;

  return (Error){0};
}

Error parse_binary(ParserState *state, Expr *left, Expr *out) {
  Token op = state->previous;

  switch (op.type) {
  case TokenStar:
  case TokenSlash:
  case TokenMinus:
  case TokenPlus:
    break;
  default:
    return Error_new(op.line, op.col, ErrorUnexpectedOperator);
  }

  PrecedenceRule rule = precedence_rules[op.type];

  Error err = parse_precedence(state, rule.precedence + 1);
  if (err.type != ErrorNone) {
    return err;
  }

  out->type = ExprTypeBinary;
  out->line = op.line;
  out->col = op.col;

  out->as.binary.left = left;
  out->as.binary.right = state->last_expr;
  out->as.binary.op = op.type;

  return (Error){0};
}

Error parse_precedence(ParserState *state, Precedence precedence) {
  Error err = ParserState_advance(state);
  if (err.type != ErrorNone) {
    return err;
  }

  Expr *left = NULL;

  // prefix
  {
    Token op = state->previous;
    PrecedenceRule rule = precedence_rules[op.type];

    if (rule.prefix == NULL) {
      return Error_new(op.line, op.col, ErrorExpectedExpression);
    }

    // parse left
    left = ParserState_alloc_expr(state);
    Error err = rule.prefix(state, left);
    if (err.type != ErrorNone) {
      return err;
    }
  }

  // infix
  while (1) {
    {
      PrecedenceRule op = precedence_rules[state->current.type];
      if (precedence > op.precedence) {
        break;
      }

      Error err = ParserState_advance(state);
      if (err.type == ErrorUnexpectedEOF) {
        break;
      }
      if (err.type != ErrorNone) {
        return err;
      }
    }

    Token op = state->previous;
    PrecedenceRule rule = precedence_rules[op.type];
    if (rule.infix == NULL) {
      break;
    }

    // parse infix
    Expr *right = ParserState_alloc_expr(state);
    Error err = rule.infix(state, left, right);
    if (err.type != ErrorNone) {
      return err;
    }
    left = right;
  }

  state->last_expr = left;

  return (Error){0};
}

// 5 * 3
//
// push 5
// push 3
// mul
//
// 5 + 5 * 3
// push 5
// push 5
// push 3
// mul
// add

Error emit_expr(Expr *expr, Instructions *output) {
  if (expr == NULL) {
    return (Error){0};
  }

  switch (expr->type) {
  case ExprTypeLitNumber: {
    Instruction ix = {0};
    ix.op = OpPush;
    ix.value = expr->as.number.value;
    Instructions_add(output, ix);
    break;
  }

  case ExprTypeUnary: {
    switch (expr->as.unary.op) {
    case TokenPlus: {
      Error err = emit_expr(expr->as.unary.operand, output);
      if (err.type != ErrorNone) {
        return err;
      }
      break;
    }

    case TokenMinus: {
      Error err = emit_expr(expr->as.unary.operand, output);
      if (err.type != ErrorNone) {
        return err;
      }
      Instructions_add(output, (Instruction){OpNeg});
      break;
    }

    default:
      return Error_new(expr->line, expr->col, ErrorUnexpectedOperator);
    }
    break;
  }

  case ExprTypeBinary: {
    Error err = emit_expr(expr->as.binary.left, output);
    if (err.type != ErrorNone) {
      return err;
    }

    err = emit_expr(expr->as.binary.right, output);
    if (err.type != ErrorNone) {
      return err;
    }

    switch (expr->as.binary.op) {
    case TokenPlus:
      Instructions_add(output, (Instruction){OpAdd});
      break;
    case TokenMinus:
      Instructions_add(output, (Instruction){OpSub});
      break;
    case TokenStar:
      Instructions_add(output, (Instruction){OpMul});
      break;
    case TokenSlash:
      Instructions_add(output, (Instruction){OpDiv});
      break;
    default:
      return Error_new(expr->line, expr->col, ErrorUnexpectedOperator);
    }

    break;
  }
  }

  return (Error){0};
}

int main(int argc, char *argv[]) {
  if (argc == 2) {
    // file

  } else if (argc == 1) {
    // repl
    char *line = NULL;
    size_t cap = 0;

    Tokens tokens;
    Tokens_init(&tokens, 1024);

    Expressions expr_arena;
    Expressions_init(&expr_arena, 1024);

    Instructions instructions;
    Instructions_init(&instructions, 1024);

    VM vm;
    VM_init(&vm);

    while (1) {
      Tokens_clear(&tokens);
      Instructions_clear(&instructions);

      printf("> ");

      ssize_t len = getline(&line, &cap, stdin);
      if (len == -1) {
        break;
      }

      // process line

      Error error = tokenize(&tokens, line);
      if (error.type != ErrorNone) {
        Error_print(&error, line);
        continue;
      }

      ParserState parser;
      ParserState_init(&parser, line, &tokens, &expr_arena);

      error = parse_precedence(&parser, PrecedenceNone);
      if (error.type != ErrorNone) {
        Error_print(&error, line);
        continue;
      }

      error = emit_expr(parser.last_expr, &instructions);
      if (error.type != ErrorNone) {
        Error_print(&error, line);
        continue;
      }

      Expr_print(parser.last_expr);
      printf("\n");

      error = VM_run(&vm, &instructions);
      if (error.type != ErrorNone) {
        Error_print(&error, line);
      }

      printf("%f\n", vm.stack.items[0]);
    }

  } else {
    printf("Usage: %s <filename?>\n", argv[0]);
    return 1;
  }

  return 0;
}
