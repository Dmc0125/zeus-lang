package toolchain

import "base:runtime"
import "core:fmt"
import "core:mem"

Interpreter :: struct {
	print:     bool,
	variables: map[string]f32,
}

interpreter_init :: proc(i: ^Interpreter, print: bool, allocator: mem.Allocator) {
	i.print = print
	i.variables = make(map[string]f32, allocator = allocator)
}

interpret_expr :: proc(i: ^Interpreter, n: Expression) -> (val: f32, err: Error) {
	#partial switch n in n {
	case Literal:
		switch l in n {
		case ^Literal_Number:
			return l.value, nil
		case ^Literal_Ident:
			exists: bool
			if val, exists = i.variables[l.name]; !exists {
				// TODO: error
				panic("variable is not defined")
			}
			return
		}
	case ^Unary_Expr:
		val = interpret_expr(i, n.right) or_return
		if n.op.type == .Minus {
			val = -val
		}
		return
	case ^Binary_Expr:
		left := interpret_expr(i, n.left) or_return
		right := interpret_expr(i, n.right) or_return
		#partial switch n.op.type {
		case .Plus:
			val = left + right
		case .Minus:
			val = left - right
		case .Star:
			val = left * right
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
		}

		return
	}

	b: [1024]byte
	assert(false, fmt.bprintf(b[:], "not an expression: %v", n))
	return
}

interpreter_run :: proc(i: ^Interpreter, stmt: Statement) -> (err: Error) {
	#partial switch stmt in stmt {
	case ^Variable_Declaration:
		if stmt.initializer != nil {
			val := interpret_expr(i, stmt.initializer) or_return
			i.variables[stmt.ident] = val
		} else {
			i.variables[stmt.ident] = 0
		}
	case ^Print_Statement:
		assert(stmt.expr != nil)
		val := interpret_expr(i, stmt.expr) or_return
		fmt.println(val)
	case:
		b: [1024]byte
		assert(false, fmt.bprintf(b[:], "not a statement: %v", stmt))
	}
	return
}
