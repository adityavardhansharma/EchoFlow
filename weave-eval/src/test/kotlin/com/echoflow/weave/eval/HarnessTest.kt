package com.echoflow.weave.eval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The harness grades known-good replies as passing and known-bad ones as failing. */
class HarnessTest {
    private fun task(id: String) = Tasks.all.first { it.id == id }
    private fun reply(body: String) = "Here you go.\n\n```weave\n$body\n```\n"

    @Test fun `task set has 60 tasks in 6 categories and 20 controls`() {
        val tasks = Tasks.all.filter { !it.control }
        assertEquals(60, tasks.size)
        assertEquals(6, tasks.map { it.category }.toSet().size)
        assertTrue(tasks.groupBy { it.category }.values.all { it.size == 10 })
        assertEquals(20, Tasks.controls.size)
        assertEquals(70, Tasks.forTier(2).size)
        assertEquals(Tasks.all.size, Tasks.all.map { it.id }.toSet().size)
    }

    @Test fun `fences are top-level weave blocks only`() {
        val r = "a\n```weave\nroot = Text(\"x\")\n```\n  ```weave\nindented\n  ```\n```Weave\nno\n```\n```weave edit loan\na = 1\n```\n~~~weave\nroot = Text(\"y\")\n~~~\n"
        val blocks = Scoring.weaveBlocks(r)
        assertEquals(listOf("root = Text(\"x\")", "root = Text(\"y\")"), blocks.map { it.body })
    }

    @Test fun `number reading handles Indian grouping and compact suffixes`() {
        assertEquals(listOf(43391.0), CheckRunner.numbersIn("₹43,391"))
        assertEquals(listOf(1234568.0), CheckRunner.numbersIn("12,34,568"))
        assertEquals(listOf(2.5e7, 2.5, 5600.0, 5.6), CheckRunner.numbersIn("2.5 Cr and 5.6K"))
        assertEquals(listOf(1400.0), CheckRunner.numbersIn("1,400 Last month"))
    }

    @Test fun `reference EMI calculator completes the task`() {
        val body = """
            @weave 1 id: emi title: "Home loan EMI"
            ${'$'}rate = 8
            root = Card(gap: m) [
              Slider(${'$'}rate, min: 6, max: 14, step: 0.05, label: "Interest rate", suffix: "%"),
              Metric("Monthly EMI", emi(5000000, ${'$'}rate / 100, 20), format: inr)
            ]
        """.trimIndent()
        val s = Scoring.score(task("calc_emi"), reply(body), 2)
        assertTrue(s.detail, s.completed); assertTrue(s.parseOk); assertTrue(s.renders); assertEquals(0, s.dropped)
    }

    @Test fun `a wrong formula fails the task but still renders`() {
        val body = "\$rate = 8\nroot = Card [Slider(\$rate, label: \"Rate\"), Metric(\"EMI\", 5000000 * \$rate / 100, format: inr)]"
        val s = Scoring.score(task("calc_emi"), reply(body), 2)
        assertTrue(s.renders); assertFalse(s.completed)
    }

    @Test fun `reference tip splitter and BMI`() {
        val tip = "\$bill = 2400\n\$people = 4\nroot = Column [TextField(\$bill, label: \"Bill\", kind: number), Slider(\$people, min: 1, max: 20, label: \"People\"), Metric(\"Each pays\", \$bill * 1.1 / \$people, format: inr)]"
        assertTrue(Scoring.score(task("calc_tip"), reply(tip), 2).completed)
        val bmi = "\$w = 60\n\$h = 160\nroot = Column [Slider(\$w, min: 30, max: 150, label: \"Weight (kg)\"), Slider(\$h, min: 100, max: 220, label: \"Height (cm)\"), Metric(\"BMI\", \$w / pow(\$h / 100, 2), format: \"dec(1)\")]"
        val s = Scoring.score(task("calc_bmi"), reply(bmi), 2)
        assertTrue(s.detail, s.completed)
    }

    @Test fun `reference data answer with a chart and a total`() {
        val body = "rows = [{c: \"Rent\", v: 18000}, {c: \"Food\", v: 9000}, {c: \"Travel\", v: 4000}, {c: \"Bills\", v: 3500}, {c: \"Fun\", v: 5500}]\nroot = Card [BarChart(rows, x: \"c\", y: \"v\"), Table(rows), Metric(\"Total\", sum(rows, \"v\"), format: inr)]"
        val s = Scoring.score(task("data_expenses"), reply(body), 2)
        assertTrue(s.detail, s.completed)
    }

    @Test fun `controls answered in plain text are not over-use`() {
        val c = Tasks.controls.first()
        val plain = Scoring.score(c, "Canberra is the capital of Australia.", 2)
        assertEquals(0, plain.blocks)
        val block = Scoring.score(c, reply("root = Text(\"Canberra\")"), 2)
        assertEquals(1, block.blocks)
        val report = ModelReport("m", 2, listOf(plain, block))
        assertEquals(0.5, report.overUse, 1e-9)
        // An unanswered control is not a block, but it still counts in the 20 (07 §10.2).
        val missing = ModelReport("m", 2, listOf(Scoring.score(c, null, 2)))
        assertEquals(0.0, missing.overUse, 1e-9)
        assertEquals(1, missing.missing)
    }

    @Test fun `missing replies count as failures, not crashes`() {
        val s = Scoring.score(task("calc_emi"), null, 2)
        assertFalse(s.responded); assertFalse(s.completed)
    }
}

class RevealTextTest {
    @Test fun `text behind a reveal button counts`() {
        val t = Tasks.all.first { it.id == "learn_photo" }
        val body = "\$show = false\nroot = Column [Text(\"Which pigment?\"), Button(\"Reveal\", onTap: !toggle(\$show)), when(\$show, Text(\"Chlorophyll\"))]"
        org.junit.Assert.assertTrue(Scoring.score(t, "```weave\n$body\n```", 2).completed)
    }

    private fun session(body: String) = com.echoflow.weave.core.eval.Session(com.echoflow.weave.core.program.Compiler.compile(body))

    @Test fun `hidden text does not pass a text check`() {
        val hidden = session("root = Column [Text(\"Answer below\"), Text(\"81\", show: false)]")
        assertFalse(CheckRunner(hidden).run(listOf(Check.Text("81"))).pass)
        val shown = session("root = Column [Text(\"Answer below\"), Text(\"81\")]")
        assertTrue(CheckRunner(shown).run(listOf(Check.Text("81"))).pass)
    }

    @Test fun `fields a table does not show are not read`() {
        val body = "rows = [{name: \"A\", secret: 81}]\nroot = Table(rows, cols: [{header: \"Name\", value: \"name\"}])"
        assertFalse(CheckRunner(session(body)).run(listOf(Check.Text("81"))).pass)
        assertTrue(CheckRunner(session("rows = [{name: \"A\", score: 81}]\nroot = Table(rows)")).run(listOf(Check.Text("81"))).pass)
    }

    @Test fun `disabled controls don't count`() {
        val off = session("\$n = 1\nroot = Column [Slider(\$n, 0, 10, label: \"Amount\", disabled: true), Text(\"{\$n * 2}\")]")
        assertFalse(CheckRunner(off).run(listOf(Check.Interactive)).pass)
        assertFalse(CheckRunner(off).run(listOf(Check.Input("Amount", 4.0))).pass)
        val on = session("\$n = 1\nroot = Column [Slider(\$n, 0, 10, label: \"Amount\"), Text(\"{\$n * 2}\")]")
        assertTrue(CheckRunner(on).run(listOf(Check.Input("Amount", 4.0), Check.Number(8.0))).pass)
    }

    @Test fun `text checks don't fire a button an earlier tap disabled`() {
        val body = "\$n = 0\nroot = Column [\n" +
            "Button(\"First\", onTap: !set(\$n, 1)),\n" +
            "Button(\"Second\", onTap: !set(\$n, 2), disabled: \$n == 1),\n" +
            "Text(\"answer {\$n}\")\n]"
        assertFalse(CheckRunner(session(body)).run(listOf(Check.Text("answer 2"))).pass)
        assertTrue(CheckRunner(session(body)).run(listOf(Check.Text("answer 1"))).pass)
    }

    @Test fun `two buttons with the same label are both tried`() {
        val body = "\$n = 0\nroot = Column [\n" +
            "Button(\"Reveal\", onTap: !set(\$n, 1)),\n" +
            "Button(\"Reveal\", onTap: !set(\$n, 2)),\n" +
            "Text(\"answer {\$n}\")\n]"
        assertTrue(CheckRunner(session(body)).run(listOf(Check.Text("answer 2"))).pass)
    }

    @Test fun `buttons rendered by a list are all tried`() {
        val body = "\$n = 0\nrows = [1, 2]\nroot = Column [\n" +
            "List(rows, render: (item, index) => Button(\"Reveal\", onTap: !set(\$n, index + 1))),\n" +
            "Text(\"answer {\$n}\")\n]"
        assertTrue(CheckRunner(session(body)).run(listOf(Check.Text("answer 2"))).pass)
    }
}
