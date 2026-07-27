package toolchain

import "base:runtime"
import "core:fmt"
import "core:mem"

Literal :: Token

Unary_Expr :: struct {
	op:    Token,
	right: Node,
}

Binary_Expr :: struct {
	left:  Node,
	right: Node,
	op:    Token,
}

Variable_Declaration :: struct {
	ident:       string,
	initializer: Node,
}

Print_Statement :: struct {
	expr: Node,
}

Node :: union {
	// Statements
	^Variable_Declaration,
	^Print_Statement,

	// Expressions
	^Literal,
	^Unary_Expr,
	^Binary_Expr,
}

node_string :: proc(n: Node, allocator: mem.Allocator) -> string {
	switch n in n {
	case ^Literal:
		return fmt.aprintf("Literal(%f)", n.value, allocator = allocator)
	case ^Unary_Expr:
		return fmt.aprintf(
			"Unary(%s%s)",
			n.op.type,
			node_string(n.right, allocator),
			allocator = allocator,
		)
	case ^Binary_Expr:
		return fmt.aprintf(
			"Binary(%s %s %s)",
			n.op.type,
			node_string(n.left, allocator),
			node_string(n.right, allocator),
			allocator = allocator,
		)
	case ^Variable_Declaration:
		if n.initializer == nil {
			return fmt.aprintf("VarDecl(%s)", n.ident, allocator = allocator)
		} else {
			return fmt.aprintf(
				"VarDecl(%s = %s)",
				n.ident,
				node_string(n.initializer, allocator),
				allocator = allocator,
			)
		}
	case ^Print_Statement:
		return fmt.aprintf("Print(%s)", node_string(n.expr, allocator), allocator = allocator)
	}
	return "nil"
}

Parser :: struct {
	allocator:  mem.Allocator,
	tokens:     []Token,
	token_idx:  int,
	statements: [dynamic]Node,
}

parser_init :: proc(p: ^Parser, tokens: []Token, allocator: mem.Allocator) {
	p.allocator = allocator
	p.tokens = tokens
	p.statements = make([dynamic]Node, 0, allocator = allocator)
}

parser_peek :: proc(p: ^Parser) -> (t: Token, ok: bool) {
	if len(p.tokens) <= p.token_idx {
		return
	}
	t = p.tokens[p.token_idx]
	ok = true
	return
}

parser_eat :: proc(p: ^Parser, expected: Token_Type) -> (t: Token, err: Error) {
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

parse_literal :: proc(p: ^Parser) -> (^Literal, Error) {
	// factor => number

	number, err := parser_eat(p, .Number)
	if err != nil {
		return nil, err
	}

	n := new(Literal, allocator = p.allocator)
	n^ = Literal {
		type      = .Number,
		value     = number.value,
		line      = number.line,
		col_start = number.col_start,
		col_end   = number.col_end,
	}
	return n, nil
}

parse_unary :: proc(p: ^Parser) -> (Node, Error) {
	// unary => '-' | '+' | factor

	if sign, ok := parser_peek(p); ok && (sign.type == .Minus || sign.type == .Plus) {
		p.token_idx += 1

		un := new(Unary_Expr, allocator = p.allocator)
		un.op = sign

		err: Error
		un.right, err = parse_unary(p)

		return un, err
	}

	return parse_literal(p)
}

parse_term :: proc(p: ^Parser) -> (n: Node, err: Error) {
	// term => unary ('*' | '/' unary )*

	n = parse_unary(p) or_return

	for {
		sign := parser_peek(p) or_break
		if sign.type != .Star && sign.type != .Slash {
			break
		}
		p.token_idx += 1

		right: Node
		right = parse_unary(p) or_return

		bn := new(Binary_Expr, allocator = p.allocator)
		bn.left = n
		bn.right = right
		bn.op = sign

		n = bn
	}

	return
}

parse_expr :: proc(p: ^Parser) -> (n: Node, err: Error) {
	// expr => unary ('+' | '-' unary)*

	n = parse_term(p) or_return

	for {
		sign := parser_peek(p) or_break
		if sign.type != .Minus && sign.type != .Plus {
			break
		}
		p.token_idx += 1

		right: Node
		right = parse_term(p) or_return

		bn := new(Binary_Expr, allocator = p.allocator)
		bn.left = n
		bn.right = right
		bn.op = sign

		n = bn
	}

	return
}

parse_variable_decl :: proc(p: ^Parser) -> (ok: bool, err: Error) {
	// variable_decl => ident ':=' expr | ident

	if token, tok := parser_peek(p); tok && token.type == .Ident {
		p.token_idx += 1

		ident, ok := token.value.(string)
		if !ok {
			err = Error_With_Message {
				message = .Expected_Identifier_Name,
				type    = .Parse,
				line    = token.line,
				col     = token.col_start,
			}
			return
		}

		ident_node := new(Variable_Declaration, allocator = p.allocator)
		ident_node.ident = ident

		if token, ok := parser_peek(p); ok && token.type == .ColonEqual {
			p.token_idx += 1
			ident_node.initializer = parse_expr(p) or_return
		}

		append(&p.statements, ident_node)
		ok = true
	}

	return
}

parse_print_stmt :: proc(p: ^Parser) -> (ok: bool, err: Error) {
	// print_stmt => 'print' expr ';'

	if token, tok := parser_peek(p); tok && token.type == .Print {
		p.token_idx += 1

		print_stmt := new(Print_Statement, allocator = p.allocator)
		print_stmt.expr = parse_expr(p) or_return

		append(&p.statements, print_stmt)
		ok = true
	}

	return
}

parse_stmt :: proc(p: ^Parser) -> (err: Error) {
	// stmt => variable_decl ';'

	ok := parse_variable_decl(p) or_return
	if !ok {
		ok = parse_print_stmt(p) or_return
	}
	if !ok {
		err = Error_With_Message {
			message = .Invalid_Token,
			type    = .Parse,
		}
		return
	}

	if token, ok := parser_peek(p); !ok {
		err = Error_With_Message {
			message = .Expected_EOF,
			type    = .Parse,
		}
		return
	} else if token.type != .Semicolon {
		err = Error_With_Message {
			message = .Expected_Semicolon,
			type    = .Parse,
			line    = token.line,
			col     = token.col_start,
		}
		return
	}

	p.token_idx += 1
	return
}

parser_run :: proc(p: ^Parser) -> (err: Error) {
	for {
		parse_stmt(p) or_return

		if token, ok := parser_peek(p); !ok {
			err = Error_With_Message {
				message = .Expected_EOF,
				type    = .Parse,
			}
			return
		} else if token.type == .EOF {
			return
		}
	}
}
