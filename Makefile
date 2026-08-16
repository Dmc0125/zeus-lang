.PHONY: build run

SOURCES := ./compiler/main.c ./compiler/vm.c

build:
	gcc $(SOURCES) -o ./compiler/compiler.bin

run: build
	./compiler/compiler.bin $(ARGS)
