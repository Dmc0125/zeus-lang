#include "vm.h"
#include "main.h"
#include "vec.h"
#include <stdio.h>

DEFINE_VEC_IMPL(Instruction, Instructions)

void VMStack_init(VM *vm) {
  VMStack *stack = &vm->stack;

  stack->items = (f64 *)malloc(sizeof(f64) * DEFAULT_STACK_CAPACITY);

  if (stack->items == NULL) {
    fprintf(stderr, "Failed to allocate memory for VM stack\n");
    exit(1);
  }

  stack->count = 0;
  stack->capacity = DEFAULT_STACK_CAPACITY;
}

void VMStack_push(VM *vm, f64 value) {
  VMStack *stack = &vm->stack;

  if (stack->count == stack->capacity) {
    stack->capacity *= 2;
    stack->items = (f64 *)realloc(stack->items, sizeof(f64) * stack->capacity);

    if (stack->items == NULL) {
      fprintf(stderr, "Failed to allocate memory for VM stack\n");
      exit(1);
    }
  }

  stack->items[stack->count] = value;
  stack->count += 1;
}

f64 VMStack_pop(VM *vm) {
  VMStack *stack = &vm->stack;

  if (stack->count == 0) {
    vm->err = Error_new(0, 0, ErrorVMStackUnderflow);
    return 0;
  }

  stack->count -= 1;
  return stack->items[stack->count];
}

void VM_init(VM *vm) {
  VMStack_init(vm);
  vm->err = (Error){0};
}

// void Instruction_print(Instruction *instruction) {
//   printf("%d %f\n", instruction->op, instruction->value);
// }

Error VM_run(VM *vm, Instructions *instructions) {
  for (size_t i = 0; i < instructions->count; i++) {
    Instruction instruction = instructions->items[i];
    switch (instruction.op) {
    case OpPush: {
      VMStack_push(vm, instruction.value);
      break;
    }
    case OpAdd: {
      f64 b = VMStack_pop(vm);
      if (vm->err.type != ErrorNone)
        return vm->err;
      f64 a = VMStack_pop(vm);
      if (vm->err.type != ErrorNone)
        return vm->err;
      VMStack_push(vm, a + b);
      break;
    }
    case OpSub: {
      f64 b = VMStack_pop(vm);
      if (vm->err.type != ErrorNone)
        return vm->err;
      f64 a = VMStack_pop(vm);
      if (vm->err.type != ErrorNone)
        return vm->err;
      VMStack_push(vm, a - b);
      break;
    }
    case OpMul: {
      f64 b = VMStack_pop(vm);
      if (vm->err.type != ErrorNone)
        return vm->err;
      f64 a = VMStack_pop(vm);
      if (vm->err.type != ErrorNone)
        return vm->err;
      VMStack_push(vm, a * b);
      break;
    }
    case OpDiv: {
      f64 b = VMStack_pop(vm);
      if (vm->err.type != ErrorNone)
        return vm->err;
      f64 a = VMStack_pop(vm);
      if (vm->err.type != ErrorNone)
        return vm->err;
      VMStack_push(vm, a / b);
      break;
    }
    case OpNeg: {
      f64 a = VMStack_pop(vm);
      if (vm->err.type != ErrorNone)
        return vm->err;
      VMStack_push(vm, -a);
      break;
    }
    }
  }

  return (Error){0};
}
