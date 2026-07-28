#+vet explicit-allocators
package toolchain

import "base:runtime"
import "core:fmt"
import "core:mem"

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

