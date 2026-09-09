#ifndef VM_H
#define VM_H

#include "main.h"
#include "vec.h"

typedef enum {
  OpPush,
  OpAdd,
  OpSub,
  OpMul,
  OpDiv,
  OpNeg,
} OpCode;

typedef struct {
  OpCode op;
  f64 value;
} Instruction;

DEFINE_VEC(Instruction, Instructions)

#define DEFAULT_STACK_CAPACITY 4096 / sizeof(f64)

typedef struct {
  f64 *items;
  size_t count;
  size_t capacity;
} VMStack;

typedef struct {
  VMStack stack;
  Error err;
} VM;

void VM_init(VM *vm);
Error VM_run(VM *vm, Instructions *instructions);

#endif
