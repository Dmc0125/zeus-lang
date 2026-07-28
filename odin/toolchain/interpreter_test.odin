package toolchain

import "core:testing"

@(test)
test_interpreter_success :: proc(t: ^testing.T) {
	input := "1 +*/4:=::2.5"
	expected := [?]Token {
		{type = .Number, value = 1, line = 1, col_start = 1, col_end = 2},
		{type = .Plus, line = 1, col_start = 3, col_end = 4},
		{type = .Star, line = 1, col_start = 4, col_end = 5},
		{type = .Slash, line = 1, col_start = 5, col_end = 6},
		{type = .Number, value = 4, line = 1, col_start = 6, col_end = 7},
		{type = .ColonEqual, line = 1, col_start = 7, col_end = 9},
		{type = .Colon, line = 1, col_start = 9, col_end = 10},
		{type = .Colon, line = 1, col_start = 10, col_end = 11},
		{type = .Number, value = 2.5, line = 1, col_start = 11, col_end = 14},
		{type = .EOF, line = 1, col_start = 14, col_end = 15},
	}

	tokenizer: Tokenizer
	tokenizer_init(&tokenizer, input, allocator = context.allocator)

	tokens, err := tokenizer_run(&tokenizer)
	if !testing.expect_value(t, err, nil) {
		return
	}

	if !testing.expect_value(t, len(tokens), len(expected)) {
		return
	}

	for token, i in tokens {
		if !testing.expect_value(t, token, expected[i]) {
			return
		}
	}
}

@(test)
test_interpreter_invalid_token :: proc(t: ^testing.T) {
	input := "+."
	expected_str := "invalid token \".\" at line 1 col 2"

	tokenizer: Tokenizer
	tokenizer_init(&tokenizer, input, allocator = context.allocator)

	tokens, err := tokenizer_run(&tokenizer)

}
