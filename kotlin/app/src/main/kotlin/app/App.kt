package app

import java.nio.file.Path
import kotlin.io.path.readText
import lang.*

fun main(args: Array<String>) {
    if (args.size == 0) {
        // repl

        val printer = BufferedPrinter()
        val analyzer = Analyzer()
        val interpreter = Interpreter(printer)

        println("Welcome to the REPL. Type 'exit' to quit")

        while (true) {
            print("> ")

            val line = readLine() ?: break
            if (line == "exit") break

            try {
                val tokenizer = Tokenizer(line)
                val tokens = tokenizer.run()
                if (tokenizer.errors.isNotEmpty()) {
                    println(createMessage(line, tokenizer.errors))
                    continue
                }

                val (statements, parserErrors) = Parser(tokens).parseProgram()
                if (parserErrors.isNotEmpty()) {
                    println(createMessage(line, parserErrors))
                }

                val analyzerErrors = analyzer.analyzeProgram(statements)
                if (analyzerErrors.isNotEmpty()) {
                    println(createMessage(line, analyzerErrors))
                }

                if (statements.isNotEmpty()) {
                    interpreter.interpretProgram(statements)
                }
            } catch (e: LangError) {
                println(e.construct(line))
            }

            val flushed = printer.sb.length > 0
            printer.flush()
            if (flushed) {
                println("")
            }
        }
    } else {
        // file

        var filepath = args[0]

        if (!Path.of(filepath).isAbsolute()) {
            val workingDir = Path.of(".").toAbsolutePath().normalize()
            filepath = workingDir.toString() + "/" + filepath
        }

        val file = Path.of(filepath)
        val source = file.readText()
        val printer = BufferedPrinter()

        try {
            val tokenizer = Tokenizer(source)
            val tokens = tokenizer.run()
            if (tokenizer.errors.isNotEmpty()) {
                println(createMessage(source, tokenizer.errors))
                return
            }

            val parser = Parser(tokens)
            val (statements, parserErrors) = parser.parseProgram()
            if (parserErrors.isNotEmpty()) {
                println(createMessage(source, parserErrors))
                return
            }

            val analyzer = Analyzer()
            val analyzerErrors = analyzer.analyzeProgram(statements)
            if (analyzerErrors.isNotEmpty()) {
                println(createMessage(source, analyzerErrors))
                return
            }

            val interpreter = Interpreter(printer)
            interpreter.interpretProgram(statements)
        } catch (e: LangError) {
            println(e.construct(source))
        }

        printer.flush()
    }
}
