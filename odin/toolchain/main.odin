#+vet explicit-allocators
package toolchain

import "base:runtime"
import "core:fmt"
import "core:mem"
import "core:mem/virtual"

Error_Invalid_Token :: struct {
	token: string,
	line:  int,
	col:   int,
}

error_invalid_token_string :: proc(e: Error_Invalid_Token, allocator: mem.Allocator) -> string {
	return fmt.aprintf(
		"invalid token: %s at line %d col %d",
		e.token,
		e.line,
		e.col,
		allocator = allocator,
	)
}

Error_Message :: enum {
	// Tokenizer
	Invalid_Number,

	// Parser
	Invalid_Token,
	Expected_Identifier_Name,
	Expected_Semicolon,
	Expected_EOF,

	// Runtime
	Division_By_Zero,
}

error_message_string :: proc(e: Error_Message, allocator: mem.Allocator) -> string {
	switch e {
	case .Invalid_Number:
		return "invalid number"
	case .Invalid_Token:
		return "invalid token"
	case .Expected_Identifier_Name:
		return "expected identifier name"
	case .Expected_Semicolon:
		return "expected semicolon"
	case .Expected_EOF:
		return "expected EOF"
	case .Division_By_Zero:
		return "division by zero"
	}
	assert(false, fmt.aprintf("unreachable: invalid error message: %v", e, allocator = allocator))
	return ""
}

Error_Type :: enum {
	Parse,
	Runtime,
}

Error_With_Message :: struct {
	message: Error_Message,
	type:    Error_Type,
	line:    int,
	col:     int,
	meta:    string,
}

Error :: union {
	runtime.Allocator_Error,
	Error_With_Message,
}

error_string :: proc(e: Error, allocator: mem.Allocator) -> string {
	switch e in e {
	case runtime.Allocator_Error:
		return fmt.aprintf("allocator error: %s", e, allocator = allocator)
	case Error_With_Message:
		msg := fmt.aprintf(
			"%s error: %s",
			e.type,
			error_message_string(e.message, allocator),
			allocator = allocator,
		)
		if e.message != .Expected_EOF {
			msg = fmt.aprintf("%s at line %d col %d", msg, e.line, e.col, allocator = allocator)
		}
		if e.meta != "" {
			msg = fmt.aprintf("%s: %s", msg, e.meta, allocator = allocator)
		}
		return msg
	}

	assert(false, fmt.aprintf("unreachable: invalid error: %v", e, allocator = allocator))
	return ""
}

Interpreter :: struct {
	print:            bool,
	variables:        map[string]f32,
	//
	scratch_arena:    virtual.Arena,
	scratch_alloc:    mem.Allocator,
	last_interpreted: string,
}

interpreter_init :: proc(i: ^Interpreter, print: bool, allocator: mem.Allocator) {
	i.print = print
	i.variables = make(map[string]f32, allocator = allocator)

	buf := make([]byte, 1024, allocator = allocator)
	if err := virtual.arena_init_buffer(&i.scratch_arena, buf[:]); err != nil {
		assert(false, fmt.aprintf("failed to initialize arena: %v", err, allocator = allocator))
	}
	i.scratch_alloc = virtual.arena_allocator(&i.scratch_arena)
}

interpret_expr :: proc(i: ^Interpreter, n: Node) -> (val: f32, err: Error) {
	#partial switch n in n {
	case ^Literal:
		if v, ok := n.value.(f32); ok {
			i.last_interpreted = fmt.aprintf("%f", v, allocator = i.scratch_alloc)
			return v, nil
		} else {
			panic("unimplemented")
		}
	case ^Unary_Expr:
		val = interpret_expr(i, n.right) or_return
		if n.op.type == .Minus {
			val = -val
		}
		i.last_interpreted = fmt.aprintf("%f", val, allocator = i.scratch_alloc)
		return
	case ^Binary_Expr:
		left := interpret_expr(i, n.left) or_return
		right := interpret_expr(i, n.right) or_return
		#partial switch n.op.type {
		case .Plus:
			val = left + right
			i.last_interpreted = fmt.aprintf("%f + %f", left, right, allocator = i.scratch_alloc)
		case .Minus:
			val = left - right
			i.last_interpreted = fmt.aprintf("%f - %f", left, right, allocator = i.scratch_alloc)
		case .Star:
			val = left * right
			i.last_interpreted = fmt.aprintf("%f * %f", left, right, allocator = i.scratch_alloc)
		case .Slash:
			if right == 0 {
				err = Error_With_Message {
					message = .Division_By_Zero,
					type    = .Runtime,
					line    = n.op.line,
					col     = n.op.col_start,
				}
				return
			}
			val = left / right
			i.last_interpreted = fmt.aprintf("%f / %f", left, right, allocator = i.scratch_alloc)
		}

		return
	}

	b: [1024]byte
	assert(false, fmt.bprintf(b[:], "not an expression: %v", n))
	return
}

interpreter_run :: proc(i: ^Interpreter, stmt: Node) -> (err: Error) {
	defer {
		i.last_interpreted = ""
		free_all(i.scratch_alloc)
	}

	#partial switch stmt in stmt {
	case ^Variable_Declaration:
		if stmt.initializer != nil {
			val := interpret_expr(i, stmt.initializer) or_return
			i.variables[stmt.ident] = val

			i.last_interpreted = fmt.aprintf(
				"%s = %s",
				stmt.ident,
				i.last_interpreted,
				allocator = i.scratch_alloc,
			)
		} else {
			i.variables[stmt.ident] = 0
			i.last_interpreted = fmt.aprintf("%s = 0", stmt.ident, allocator = i.scratch_alloc)
		}
	case ^Print_Statement:
		assert(stmt.expr != nil)
		val := interpret_expr(i, stmt.expr) or_return
		i.last_interpreted = fmt.aprintf("print %s", val, allocator = i.scratch_alloc)
		fmt.println(val)
	case:
		b: [1024]byte
		assert(false, fmt.bprintf(b[:], "not a statement: %v", stmt))
	}
	return
}
