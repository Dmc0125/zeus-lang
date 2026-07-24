#+vet explicit-allocators
package main

import "base:runtime"
import "core:fmt"
import "core:mem"
import "core:mem/virtual"
import "core:strconv"
import "core:unicode/utf8"

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
			"%s error: %s at line %d col %d",
			e.type,
			error_message_string(e.message, allocator),
			e.line,
			e.col,
			allocator = allocator,
		)
		if e.meta != "" {
			msg = fmt.aprintf("%s: %s", msg, e.meta, allocator = allocator)
		}
		return msg
	}

	assert(false, fmt.aprintf("unreachable: invalid error: %v", e, allocator = allocator))
	return ""
}

// tokenizer

Token_Type :: enum {
	None,
	Number,
	Plus,
	Minus,
	Star,
	Slash,
	EOF,
}

Token :: struct {
	type:  Token_Type,
	value: f32,
	line:  int,
	col:   int,
}

token_string :: proc(t: Token, allocator: mem.Allocator) -> string {
	switch t.type {
	case .Number:
		return fmt.aprintf("%f", t.value, allocator = allocator)
	case .Plus:
		return "+"
	case .Minus:
		return "-"
	case .Star:
		return "*"
	case .Slash:
		return "/"
	case .EOF:
		return "EOF"
	case .None:
	}
	panic("unreachable")
}

tokenize :: proc(s: string, allocator: mem.Allocator) -> ([]Token, Error) {
	tokens := make([dynamic]Token, 0, allocator = allocator)
	runes, err := utf8.string_to_runes(s, allocator = allocator)
	if err != nil {
		return nil, err
	}

	i: int
	line := 1

	for i < len(s) {
		c := runes[i]
		increment := true
		defer if increment {i += 1}

		if c == ' ' || c == '\t' {
			continue
		}
		if c == '\n' {
			line += 1
			continue
		}

		switch c {
		case '+':
			append(&tokens, Token{type = .Plus, col = i, line = line})
		case '-':
			append(&tokens, Token{type = .Minus, col = i, line = line})
		case '*':
			append(&tokens, Token{type = .Star, col = i, line = line})
		case '/':
			append(&tokens, Token{type = .Slash, col = i, line = line})
		case:
			is_number :: proc(c: rune) -> bool {
				return (c >= '0' && c <= '9') || c == '.'
			}

			if is_number(c) {
				increment = false
				start := i
				for i < len(s) && is_number(runes[i]) {
					i += 1
				}

				num, ok := strconv.parse_f32(s[start:i])
				if !ok {
					return nil, Error_With_Message {
						message = .Invalid_Number,
						type = .Parse,
						line = line,
						col = start,
						meta = s[start:i],
					}
				}

				append(&tokens, Token{type = .Number, value = num, col = start, line = line})
			} else {
				return nil, Error_With_Message {
					message = .Invalid_Token,
					type = .Parse,
					line = line,
					col = i,
					meta = s[i:i + 1],
				}
			}
		}
	}

	append(&tokens, Token{type = .EOF, col = len(s)})
	return tokens[:], nil
}

// Parser

Factor_Node :: Token

Unary_Node :: struct {
	op:    Token,
	right: Node,
}

Binary_Node :: struct {
	left:  Node,
	right: Node,
	op:    Token,
}

Node :: union {
	^Factor_Node,
	^Unary_Node,
	^Binary_Node,
}

node_string :: proc(n: Node, allocator: mem.Allocator) -> string {
	switch n in n {
	case ^Factor_Node:
		return fmt.aprintf("F(%f)", n.value, allocator = allocator)
	case ^Unary_Node:
		return fmt.aprintf(
			"U(%s%s)",
			n.op.type,
			node_string(n.right, allocator),
			allocator = allocator,
		)
	case ^Binary_Node:
		return fmt.aprintf(
			"B(%s %s %s)",
			n.op.type,
			node_string(n.left, allocator),
			node_string(n.right, allocator),
			allocator = allocator,
		)
	}
	return "nil"
}

Parser :: struct {
	allocator: mem.Allocator,
	tokens:    []Token,
	token_idx: int,
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
			col     = t.col,
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

parse_factor :: proc(p: ^Parser) -> (^Factor_Node, Error) {
	// factor => number

	number, err := parser_eat(p, .Number)
	if err != nil {
		return nil, err
	}

	n := new(Factor_Node, allocator = p.allocator)
	n.value = number.value
	n.line = number.line
	n.col = number.col
	return n, nil
}

parse_unary :: proc(p: ^Parser) -> (Node, Error) {
	// unary => '-' | '+' | factor

	if sign, ok := parser_peek(p); ok && (sign.type == .Minus || sign.type == .Plus) {
		p.token_idx += 1

		un := new(Unary_Node, allocator = p.allocator)
		un.op = sign

		err: Error
		un.right, err = parse_unary(p)

		return un, err
	}

	return parse_factor(p)
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

		bn := new(Binary_Node, allocator = p.allocator)
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

		bn := new(Binary_Node, allocator = p.allocator)
		bn.left = n
		bn.right = right
		bn.op = sign

		n = bn
	}

	return
}

parser_run :: proc(p: ^Parser) -> (n: Node, err: Error) {
	n, err = parse_expr(p)
	if err != nil {
		return
	}
	eof := p.tokens[p.token_idx]
	if eof.type != .EOF {
		err = Error_With_Message {
			message = .Expected_EOF,
			type    = .Parse,
			line    = eof.line,
			col     = eof.col,
		}
	}
	return
}

interpret :: proc(n: Node, allocator: mem.Allocator) -> (val: f32, err: Error) {
	switch n in n {
	case ^Factor_Node:
		return n.value, nil
	case ^Unary_Node:
		val = interpret(n.right, allocator) or_return
		if n.op.type == .Minus {
			val = -val
		}
		return
	case ^Binary_Node:
		left := interpret(n.left, allocator) or_return
		right := interpret(n.right, allocator) or_return
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
					col     = n.op.col,
				}
				return
			}
			val = left / right
		}

		return
	}

	assert(false, fmt.aprintf("unreachable: invalid node: %v", n, allocator = allocator))
	return
}

main :: proc() {
	arena: virtual.Arena
	if err := virtual.arena_init_growing(&arena); err != nil {
		fmt.printfln("failed to initialize arena: %v", err)
		return
	}
	allocator := virtual.arena_allocator(&arena)

	tokens, err := tokenize("3 - 2 * 4 / 0", allocator)
	if err != nil {
		fmt.println(error_string(err, allocator))
		return
	}

	for t in tokens {
		fmt.print(token_string(t, allocator))
	}
	fmt.println()

	parser := Parser {
		allocator = allocator,
		tokens    = tokens,
	}

	n: Node
	n, err = parser_run(&parser)
	if err != nil {
		fmt.println(error_string(err, allocator))
		return
	}

	fmt.println(node_string(n, allocator))

	val: f32
	val, err = interpret(n, allocator)
	if err != nil {
		fmt.println(error_string(err, allocator))
		return
	}
	fmt.println(val)
}
