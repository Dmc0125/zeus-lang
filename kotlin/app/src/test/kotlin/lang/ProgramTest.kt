package lang

import kotlin.test.*
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class Errors(val errors: List<LangError>) : Throwable()

fun runPipeline(output: PrintStream, program: String): Interpreter {
    val tokenizer = Tokenizer(program)
    val tokens = tokenizer.run()
    if (tokenizer.errors.isNotEmpty()) {
        throw Errors(tokenizer.errors)
    }

    val parser = Parser(tokens)
    val (ast, parserErrors) = parser.parseProgram()
    if (parserErrors.isNotEmpty()) {
        throw Errors(parserErrors)
    }

    val analyzer = Analyzer()
    analyzer.analyzeProgram(ast)

    val printer = BufferedPrinter(output = output)
    val interpreter = Interpreter(printer)
    interpreter.interpretProgram(ast)

    printer.flush()
    return interpreter
}

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
            val buf = ByteArrayOutputStream()
            val output = PrintStream(buf, true, Charsets.UTF_8)

            runPipeline(output, program)

            val expectedOutput = "Starting program\nAda is an adult\n82.0\n"
            assertEquals(expectedOutput, buf.toString(Charsets.UTF_8))
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
            val buf = ByteArrayOutputStream()
            val output = PrintStream(buf, true, Charsets.UTF_8)
            runPipeline(output, program)

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

            assertEquals(expectedOutput, buf.toString(Charsets.UTF_8))
        } catch (e: LangError) {
            fail("Unexpected exception: ${e.construct(program)}")
        }
    }

    @Test
    fun `test loop break`() {
        val program = """
            x := 1;

            for x < 10 {
                if x == 5 {
                    {
                        break;
                    }
                }
                x = x + 1;
            }

            println x;
        """

        try {
            val buf = ByteArrayOutputStream()
            val output = PrintStream(buf, true, Charsets.UTF_8)
            runPipeline(output, program)
            assertEquals("5.0\n", buf.toString(Charsets.UTF_8))
        } catch (e: LangError) {
            fail(e.construct(program), e)
        }
    }

    @Test
    fun `test continue`() {
        val program = """
            x := 1;
            sum := 1;

            for x := 1; x < 10; x += 1 {
                if x == 5 {
                    {
                        continue;
                    }
                }
                sum = sum + 1;
            }

            println sum;
        """

        try {
            val buf = ByteArrayOutputStream()
            val output = PrintStream(buf, true, Charsets.UTF_8)
            runPipeline(output, program)
            assertEquals("9.0\n", buf.toString(Charsets.UTF_8))
        } catch (e: LangError) {
            println(e.construct(program))
            throw e
        }
    }

    @Test
    fun `handles recursion`() {
        val program = """
            fun foo(x: number): number {
                if x == 5 {
                    return x;
                }
                print x;
                return foo(x + 1);
            }
            x := foo(1);
        """
        val buf = ByteArrayOutputStream()
        val output = PrintStream(buf, true, Charsets.UTF_8)
        val interpreter = runPipeline(output, program)
        assertEquals("1.02.03.04.0", buf.toString(Charsets.UTF_8))
        assertEquals(VariableValue.Number(5.0), interpreter.varEnv.get("x"))
    }
}
