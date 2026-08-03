package lang

import kotlin.test.*

class ProgramTest {
    @Test
    fun `interprets program with if`() {
        val program = """
            name := "Ada";
            age: number = 36;
            active: bool = true;

            println "Starting program";

            if active && age >= 18 {
                print name;
                println " is an adult";
            } else if !active {
                println "Account is inactive";
            } else {
                println "Minor account";
            }

            {
                score := (age * 2) + 10;
                println score;
            }
        """

        try {
            val tokens = tokenizerRun(program)
            val parser = Parser(tokens)
            val ast = parser.parseProgram()
            val analyzer = Analyzer()
            analyzer.analyzeProgram(ast)

            val printer = BufferedPrinter()
            val interpreter = Interpreter(printer)
            interpreter.interpretProgram(ast)

            val expectedOutput = "Starting program\nAda is an adult\n82.0\n"
            val output = printer.sb.toString()
            assertEquals(expectedOutput, output)
        } catch (e: LangError) {
            fail("Unexpected exception: ${e.construct(program)}")
        }
    }

    @Test
    fun `interprets program with for`() {
        val program = """
            limit := 10;
            sum := 0;
            i := 1;

            for i <= limit {
                if i == 5 {
                    println "Skipping 5";
                } else {
                    sum = sum + i;
                    print "Added a number";
                }

                i = i + 1;
            }

            println sum;
        """

        try {
            val tokens = tokenizerRun(program)
            val ast = Parser(tokens).parseProgram()

            val printer = BufferedPrinter()
            val analyzer = Analyzer()
            val interpreter = Interpreter(printer)

            analyzer.analyzeProgram(ast)
            interpreter.interpretProgram(ast)

            val output = printer.sb.toString()

            var sum: Double = 0.0
            for (i in 1..10) {
                if (i != 5) {
                    sum += i.toDouble()
                }
            }

            val expectedOutput = buildString {
                append("Added a number")
                append("Added a number")
                append("Added a number")
                append("Added a number")
                appendLine("Skipping 5")
                append("Added a number")
                append("Added a number")
                append("Added a number")
                append("Added a number")
                append("Added a number")
                appendLine("$sum")
            }

            assertEquals(expectedOutput, output)
        } catch (e: LangError) {
            fail("Unexpected exception: ${e.construct(program)}")
        }
    }
}
