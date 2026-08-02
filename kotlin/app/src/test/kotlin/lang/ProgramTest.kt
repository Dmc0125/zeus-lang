package lang

import kotlin.test.*

class ProgramTest {
    @Test
    fun `interprets program with if`() {
        val program = """
            name := "Ada";
            age: int = 36;
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
        val tokens = tokenizerRun(program)
        val parser = Parser(tokens)
        val ast = parser.parseProgram()
        val analyzer = Analyzer()
        analyzer.analyzeProgram(ast)

        val printer = BufferedPrinter()
        val interpreter = Interpreter(printer)
        interpreter.interpretProgram(ast)

        val expectedOutput = "Starting program\nAda is an adult\n82"
        val output = printer.sb.toString()
        assertEquals(expectedOutput, output)
    }
}
