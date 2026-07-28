package toolchain

import "base:runtime"
import "core:fmt"
import "core:mem"

Literal_Number :: struct {
	value: f32,
	line:  int,
	col:   int,
}

Literal_Ident :: struct {
	name: string,
	line: int,
	col:  int,
}

Literal :: union {
	^Literal_Number,
	^Literal_Ident,
}

Unary_Expr :: struct {
	op:    Token,
	right: Expression,
}

Binary_Expr :: struct {
	left:  Expression,
	right: Expression,
	op:    Token,
}

Expression :: union {
	Literal,
	^Unary_Expr,
	^Binary_Expr,
}

expression_string :: proc(n: Expression, allocator: mem.Allocator) -> string {
	switch n in n {
	case Literal:
		switch l in n {
		case ^Literal_Number:
			return fmt.aprintf("Literal_Number(%f)", l.value, allocator = allocator)
		case ^Literal_Ident:
			return fmt.aprintf("Literal_Ident(%s)", l.name, allocator = allocator)
		}
	case ^Unary_Expr:
		return fmt.aprintf(
			"Unary(%s%s)",
			n.op.type,
			expression_string(n.right, allocator),
			allocator = allocator,
		)
	case ^Binary_Expr:
		return fmt.aprintf(
			"Binary(%s %s %s)",
			n.op.type,
			expression_string(n.left, allocator),
			expression_string(n.right, allocator),
			allocator = allocator,
		)
	}
	assert(false, "unreachable")
	return ""
}

Variable_Declaration :: struct {
	ident:       string,
	initializer: Expression,
}

Print_Statement :: struct {
	expr: Expression,
}

Statement :: union {
	^Variable_Declaration,
	^Print_Statement,
}

statement_string :: proc(s: Statement, allocator: mem.Allocator) -> string {
	switch s in s {
	case ^Variable_Declaration:
		if s.initializer == nil {
			return fmt.aprintf("VarDecl(%s)", s.ident, allocator = allocator)
		} else {
			return fmt.aprintf(
				"VarDecl(%s = %s)",
				s.ident,
				expression_string(s.initializer, allocator),
				allocator = allocator,
			)
		}
	case ^Print_Statement:
		return fmt.aprintf(
			"Print(%s)",
			expression_string(s.expr, allocator),
			allocator = allocator,
		)
	}
	assert(false, "unreachable")
	return ""
}

ast_node_string :: proc {
	statement_string,
	expression_string,
}

Program :: struct {
	allocator:  mem.Allocator,
	statements: [dynamic]Statement,

	//
	err:        Error,
	token_idx:  int,
	tokens:     []Token,
}

parser_peek :: proc(p: ^Program) -> (t: Token, ok: bool) {
	if len(p.tokens) <= p.token_idx {
		return
	}
	t = p.tokens[p.token_idx]
	ok = true
	return
}

parser_eat :: proc(p: ^Program, expected: Token_Type) -> (t: Token, err: Error) {
	if p.token_idx >= len(p.tokens) {
		panic("unexpected EOF")
	}

	t = p.tokens[p.token_idx]
	if t.type != expected {
		err = Error_With_Message {
			message = .Invalid_Token,
			type    = .Parse,
			line    = t.line,
			col     = t.col_start,
			meta    = fmt.aprintf(
				"expected: %s, got: %s",
				expected,
				t.type,
				allocator = p.allocator,
			),
		}
		return
	}
	p.token_idx += 1
	return
}

parse_literal_number :: proc(p: ^Program) -> (Literal, Error) {
	// primary => number

	number, err := parser_eat(p, .Number)
	if err != nil {
		return nil, err
	}

	v, ok := number.value.(f32)
	assert(ok, "number value must be f32")

	n := new(Literal_Number, allocator = p.allocator)
	n^ = Literal_Number {
		value = v,
		line  = number.line,
		col   = number.col_start,
	}
	return n, nil
}

parse_unary :: proc(p: ^Program) -> (Expression, Error) {
	// unary => '-' | '+' | unary

	if sign, ok := parser_peek(p); ok && (sign.type == .Minus || sign.type == .Plus) {
		p.token_idx += 1

		un := new(Unary_Expr, allocator = p.allocator)
		un.op = sign

		err: Error
		un.right, err = parse_unary(p)

		return un, err
	}

	return parse_literal_number(p)
}

parse_factor :: proc(p: ^Program) -> (n: Expression, err: Error) {
	// factor => unary ('*' | '/' unary )*

	n = parse_unary(p) or_return

	for {
		sign := parser_peek(p) or_break
		if sign.type != .Star && sign.type != .Slash {
			break
		}
		p.token_idx += 1

		right: Expression
		right = parse_unary(p) or_return

		bn := new(Binary_Expr, allocator = p.allocator)
		bn.left = n
		bn.right = right
		bn.op = sign

		n = bn
	}

	return
}

parse_expr :: proc(p: ^Program) -> (expr: Expression, err: Error) {
	// expr => ident | (factor ('+' | '-' factor)*)

	if ident, ok := parser_peek(p); ok && ident.type == .Ident {
		p.token_idx += 1
		name, ok := ident.value.(string)
		assert(ok, "ident value must be string")

		lit := new(Literal_Ident, allocator = p.allocator)
		lit^ = Literal_Ident {
			name = name,
			line = ident.line,
			col  = ident.col_start,
		}
		expr = Literal(lit)
		return
	}

	if expr, err = parse_factor(p); err == nil {
		for {
			sign := parser_peek(p) or_break
			if sign.type != .Minus && sign.type != .Plus {
				break
			}
			p.token_idx += 1

			right: Expression
			right = parse_factor(p) or_return

			bn := new(Binary_Expr, allocator = p.allocator)
			bn.left = expr
			bn.right = right
			bn.op = sign

			expr = bn
		}
	}

	return
}

parse_variable_decl :: proc(p: ^Program) -> (ok: bool, err: Error) {
	// variable_decl => ident ':=' expr | ident

	if token, tok := parser_peek(p); tok && token.type == .Ident {
		p.token_idx += 1

		ident: string
		ident, ok = token.value.(string)
		assert(ok, "ident value must be string")

		ident_node := new(Variable_Declaration, allocator = p.allocator)
		ident_node.ident = ident

		if token, tok := parser_peek(p); tok && token.type == .ColonEqual {
			p.token_idx += 1
			ident_node.initializer = parse_expr(p) or_return
		}

		append(&p.statements, ident_node)
		ok = true
	}

	return
}

parse_print_stmt :: proc(p: ^Program) -> (ok: bool, err: Error) {
	// print_stmt => 'print' expr

	if token, tok := parser_peek(p); tok && token.type == .Print {
		p.token_idx += 1

		print_stmt := new(Print_Statement, allocator = p.allocator)
		print_stmt.expr = parse_expr(p) or_return

		append(&p.statements, print_stmt)
		ok = true
	}

	return
}

parse_stmt :: proc(p: ^Program) {
	// stmt => variable_decl ';'

	parse :: proc(p: ^Program) -> (err: Error) {
		if ok := parse_variable_decl(p) or_return; ok {
			return
		}
		if ok := parse_print_stmt(p) or_return; ok {
			return
		}
		return Error_With_Message{message = .Invalid_Token, type = .Parse}
	}

	if err := parse(p); err != nil {
		p.err = err
		return
	}

	if token, err := parser_eat(p, .Semicolon); err != nil {
		p.err = err
	}

	return
}

parse_program :: proc(
	tokens: []Token,
	allocator: mem.Allocator,
) -> (
	program: Program,
	err: Error,
) {
	program = Program {
		allocator  = allocator,
		statements = make([dynamic]Statement, 0, allocator = allocator),
		tokens     = tokens,
	}

	for {
		parse_stmt(&program)

		if program.err != nil {
			err = program.err
			return
		}

		if token, ok := parser_peek(&program); !ok {
			err = Error_With_Message {
				message = .Expected_EOF,
				type    = .Parse,
			}
			return
		} else if token.type == .EOF {
			return
		}
	}

	return
}
