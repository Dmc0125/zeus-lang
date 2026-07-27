#+vet explicit-allocators
package main

import "base:runtime"
import "core:bufio"
import "core:flags"
import "core:fmt"
import "core:mem/virtual"
import "core:os"
import os_old "core:os/old"

import "toolchain"

Repl_Mode :: enum {
    None,
	Tokenizer, // prints tokens
	Parser, // prints AST
	Interpreter, // prints interpreted value
}

Args :: struct {
	file:      ^os_old.Handle `args="pos=0,file=rwc" usage:"Input file"`,
	repl_mode: Repl_Mode `usage:"Repl mode"`,
}

main :: proc() {
	arena: virtual.Arena
	if err := virtual.arena_init_growing(&arena); err != nil {
		fmt.printfln("failed to initialize arena: %v", err)
		return
	}
	allocator := virtual.arena_allocator(&arena)

	args: Args
	flags.parse_or_exit(&args, os.args, .Unix, allocator = allocator)

	if args.file == nil {
		// launch repl
		fmt.print("REPL:\n> ")

		scanner := bufio.Scanner{}
		bufio.scanner_init(&scanner, os.to_stream(os.stdin), allocator)

		interpreter: toolchain.Interpreter
		toolchain.interpreter_init(&interpreter, true, allocator)

		for bufio.scanner_scan(&scanner) {
			line := bufio.scanner_text(&scanner)

			if line == "exit" {
				break
			}

			defer fmt.print("> ")

			if line == "" {
				continue
			}

			tokenizer: toolchain.Tokenizer
			toolchain.tokenizer_init(&tokenizer, line, allocator)
			tokens, err := toolchain.tokenizer_run(&tokenizer)
			if err != nil {
				fmt.println(toolchain.error_string(err, allocator))
				continue
			}

			if args.repl_mode == .Tokenizer {
				fmt.println(tokens)
				continue
			}

			parser: toolchain.Parser
			toolchain.parser_init(&parser, tokens, allocator)

			if err = toolchain.parser_run(&parser); err != nil {
				fmt.println(toolchain.error_string(err, allocator))
				continue
			}

			if args.repl_mode == .Parser {
				for stmt in parser.statements {
					fmt.printf("%s;\n", toolchain.node_string(stmt, allocator))
				}
				continue
			}

			for stmt in parser.statements {
				if err = toolchain.interpreter_run(&interpreter, stmt); err != nil {
					fmt.println(toolchain.error_string(err, allocator))
				}
				// fmt.println(interpreter.last_interpreted)
			}
		}
	} else {
		// parse file
	}
}
