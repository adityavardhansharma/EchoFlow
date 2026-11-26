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
        assertEquals(listOf(2.5e7, 5600.0), CheckRunner.numbersIn("2.5 Cr and 5.6K"))
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
    }

    @Test fun `missing replies count as failures, not crashes`() {
        val s = Scoring.score(task("calc_emi"), null, 2)
        assertFalse(s.responded); assertFalse(s.completed)
    }
}
