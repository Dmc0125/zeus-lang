#+vet explicit-allocators
package main

import "base:runtime"
import "core:fmt"
import "core:mem"
import "core:mem/virtual"
import "core:strconv"
import "core:unicode/utf8"

Error :: union {
	string,
	runtime.Allocator_Error,
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
	pos:   int,
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
	for i < len(s) {
		c := runes[i]
		increment := true
		defer if increment {i += 1}

		if c == ' ' || c == '\t' || c == '\n' {
			continue
		}

		switch c {
		case '+':
			append(&tokens, Token{type = .Plus, pos = i})
		case '-':
			append(&tokens, Token{type = .Minus, pos = i})
		case '*':
			append(&tokens, Token{type = .Star, pos = i})
		case '/':
			append(&tokens, Token{type = .Slash, pos = i})
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
					return nil, fmt.aprintf(
						"invalid number: %s at %d",
						s[start:i],
						start,
						allocator = allocator,
					)
				}

				append(&tokens, Token{type = .Number, value = num, pos = start})
			}
		}
	}

	append(&tokens, Token{type = .EOF, pos = len(s)})
	return tokens[:], nil
}

// Parser

Factor_Node :: struct {
	value: f32,
}

Unary_Node :: struct {
	op:    Token_Type,
	right: Node,
}

Binary_Node :: struct {
	left:  Node,
	right: Node,
	op:    Token_Type,
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
		return fmt.aprintf("U(%d%s)", n.op, node_string(n.right, allocator), allocator = allocator)
	case ^Binary_Node:
		return fmt.aprintf(
			"B(%d %s %s)",
			n.op,
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
		err = fmt.aprintf("expected %d, got %d", expected, t.type, allocator = p.allocator)
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
	return n, nil
}

parse_unary :: proc(p: ^Parser) -> (Node, Error) {
	// unary => '-' | '+' | factor

	if sign, ok := parser_peek(p); ok && (sign.type == .Minus || sign.type == .Plus) {
		p.token_idx += 1

		un := new(Unary_Node, allocator = p.allocator)
		un.op = sign.type

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
		bn.op = sign.type

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
		bn.op = sign.type

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
		err = "expected EOF"
	}
	return
}

main :: proc() {
	arena: virtual.Arena
	if err := virtual.arena_init_growing(&arena); err != nil {
		fmt.printfln("failed to initialize arena: %v", err)
		return
	}
	allocator := virtual.arena_allocator(&arena)

	tokens, err := tokenize("5 + 3 - 2 * 4 / 5", allocator)
	if err != nil {
		fmt.println(err)
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
		fmt.println(err)
		return
	}

	fmt.println(node_string(n, allocator))
}
