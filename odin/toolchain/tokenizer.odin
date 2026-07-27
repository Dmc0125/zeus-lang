#+vet explicit-allocators
package toolchain

import "core:fmt"
import "core:mem"
import "core:strconv"

Token_Type :: enum {
	None,

	//
	Number,
	Plus,
	Minus,
	Star,
	Slash,

	//
	Semicolon,
	Colon,
	Equal,
	ColonEqual,

	//
	Ident,
	Print,

	//
	EOF,
}

Token_Value :: union {
	f32,
	string,
}

Token :: struct {
	type:               Token_Type,
	value:              Token_Value,
	line:               int,
	col_start, col_end: int,
}

token_string :: proc(t: Token, allocator: mem.Allocator) -> string {
	switch t.type {
	case .Number:
		return fmt.aprintf("Num(%f)", t.value, allocator = allocator)
	case .Plus:
		return "+"
	case .Minus:
		return "-"
	case .Star:
		return "*"
	case .Slash:
		return "/"
	case .Semicolon:
		return ";"
	case .Colon:
		return ":"
	case .Equal:
		return "="
	case .ColonEqual:
		return ":="
	case .Ident:
		return fmt.aprintf("Ident(%s)", t.value, allocator = allocator)
	case .Print:
		return fmt.aprintf("Print(%s)", t.value, allocator = allocator)
	case .EOF:
		return "EOF"
	case .None:
	}
	panic("unreachable")
}

Tokenizer :: struct {
	allocator: mem.Allocator,
	s:         string,
	i:         int,
	col, line: int,
}

tokenizer_init :: proc(t: ^Tokenizer, s: string, allocator: mem.Allocator) {
	t.allocator = allocator
	t.s = s
	t.i = 0
	return
}

tokenizer_peek :: proc(t: ^Tokenizer) -> (r: byte, ok: bool) {
	if len(t.s) <= t.i {
		return
	}
	r = t.s[t.i]
	ok = true
	return
}

tokenizer_eat_keyword :: proc(t: ^Tokenizer, s: string) -> bool {
	if t.i + len(s) > len(t.s) {
		return false
	}

	for i in 0 ..< len(s) {
		if t.s[t.i + i] != s[i] {
			return false
		}
	}

	if len(t.s) <= t.i + len(s) {
		return false
	}
	if t.s[t.i + len(s)] != ' ' {
		return false
	}

	t.i += len(s)
	return true
}

tokenizer_run :: proc(t: ^Tokenizer) -> ([]Token, Error) {
	tokens, err := make([dynamic]Token, 0, allocator = t.allocator)
	if err != nil {
		return nil, err
	}

	token_single := proc(type: Token_Type, i, line: int, value: Token_Value = 0) -> Token {
		return Token{type = type, col_start = i, line = line, col_end = i + 1, value = value}
	}

	token_multi := proc(type: Token_Type, start, end, line: int, value: Token_Value = 0) -> Token {
		return Token{type = type, col_start = start, col_end = end, line = line, value = value}
	}

	line := 1
	col := 1

	for t.i < len(t.s) {
		c := t.s[t.i]
		increment := true
		defer if increment {t.i += 1}

		if c == '\n' {
			line += 1
			col = 1
			continue
		}

		defer col += 1

		if c == ' ' || c == '\t' {
			continue
		}


		switch c {
		case '+':
			append(&tokens, token_single(.Plus, col, line))
		case '-':
			append(&tokens, token_single(.Minus, col, line))
		case '*':
			append(&tokens, token_single(.Star, col, line))
		case '/':
			append(&tokens, token_single(.Slash, col, line))
		case ';':
			append(&tokens, token_single(.Semicolon, col, line))
		case ':':
			increment = false
			t.i += 1

			if nb, ok := tokenizer_peek(t); nb == '=' {
				append(&tokens, token_multi(.ColonEqual, col, col + 1, line))
				t.i += 1
			} else {
				append(&tokens, token_single(.Colon, col, line))
			}
		case:
			is_number :: proc(c: byte) -> bool {
				return (c >= '0' && c <= '9') || c == '.'
			}

			is_alpha :: proc(c: byte) -> bool {
				return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_'
			}

			if is_number(c) {
				increment = false

				start_col := col
				start := t.i
				for t.i < len(t.s) && is_number(t.s[t.i]) {
					col += 1
					t.i += 1
				}

				num, ok := strconv.parse_f32(t.s[start:t.i])
				if !ok {
					return nil, Error_With_Message {
						message = .Invalid_Number,
						type = .Parse,
						line = line,
						col = start_col,
						meta = t.s[start:t.i],
					}
				}

				append(&tokens, token_multi(.Number, start_col, col, line, num))
			} else if (is_alpha(c)) {
				increment = false

				if tokenizer_eat_keyword(t, "print") {
					append(&tokens, token_single(.Print, col, line))
				} else {
					start_col := col
					start := t.i
					for t.i < len(t.s) && (is_alpha(t.s[t.i]) || is_number(t.s[t.i])) {
						col += 1
						t.i += 1
					}

					// TODO: check for reserved words
					// if c, ok := tokenizer_peek(t); ok {
					//     if c != ' ' && c != '=' && c
					// }

					ident := t.s[start:t.i]
					append(&tokens, token_multi(.Ident, start_col, col, line, ident))
				}
			} else {
				return nil, Error_With_Message {
					message = .Invalid_Token,
					type = .Parse,
					line = line,
					col = col,
					meta = t.s[t.i:t.i + 1],
				}
			}
		}
	}

	append(&tokens, token_single(.EOF, col, line))
	return tokens[:], nil
}
