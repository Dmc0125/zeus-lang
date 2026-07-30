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

            val tokens = tokenizerRun(line)
            val parser = Parser(tokens)
            val statements = parser.parseProgram()

            analyzer.analyzeProgram(statements)
            interpreter.interpretProgram(statements)

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
        val text = file.readText()

        // TODO: handle exceptions

        val tokens = tokenizerRun(text)
        val parser = Parser(tokens)
        val statements = parser.parseProgram()

        val analyzer = Analyzer()
        analyzer.analyzeProgram(statements)

        val printer = BufferedPrinter()
        val interpreter = Interpreter(printer)
        interpreter.interpretProgram(statements)
        printer.flush()
    }
}
