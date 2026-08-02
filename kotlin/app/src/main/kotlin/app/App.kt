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
                val tokens = tokenizerRun(line)
                val statements = Parser(tokens).parseProgram()

                analyzer.analyzeProgram(statements)
                interpreter.interpretProgram(statements)
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
            val tokens = tokenizerRun(source)
            val parser = Parser(tokens)
            val statements = parser.parseProgram()

            val analyzer = Analyzer()
            analyzer.analyzeProgram(statements)

            val interpreter = Interpreter(printer)
            interpreter.interpretProgram(statements)
        } catch (e: LangError) {
            println(e.construct(source))
        }

        printer.flush()
    }
}
