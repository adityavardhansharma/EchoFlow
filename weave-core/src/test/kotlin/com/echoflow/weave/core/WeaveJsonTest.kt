package com.echoflow.weave.core

import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.eval.Session
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.json.JsonLowering
import com.echoflow.weave.core.program.Compiler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 07 §2 T-120–T-124: Weave-JSON lowers to the same program as source. */
class WeaveJsonTest {
    private fun prog(json: String) = Compiler.compile(JsonLowering.lower(json)!!.source, BuiltinCatalog)

    @Test fun `T-120 the 02 12-1 example equals its source form`() {
        val json = """{"weave": 1, "id": "loan", "title": "Home loan", "size": "card", "icon": "savings",
          "state": {"principal": 5000000, "rate": 8.5, "years": 20, "draft": {"=": "\"\"", "transient": true}},
          "let": {"emi": "monthly(${'$'}principal, ${'$'}rate, ${'$'}years)"},
          "fn": {"monthly(p, r, y)": "p * (r/1200) * pow(1 + r/1200, y*12) / (pow(1 + r/1200, y*12) - 1)"},
          "nodes": {"root": {"t": "Card", "a": {"pad": "l"}, "c": ["rate", "kpi"]},
                    "rate": {"t": "Slider", "a": {"value": "${'$'}rate", "min": 6, "max": 14}},
                    "kpi": {"t": "Metric", "a": {"label": "Monthly EMI", "value": "=emi", "format": "inr"}}},
          "on": [{"trigger": "change(${'$'}rate)", "when": "${'$'}rate > 10", "do": "!haptic(tick)"}],
          "export": ["emi", "${'$'}rate"]}"""
        val src = "@weave 1 id: loan title: \"Home loan\" size: card icon: savings\n\$principal = 5000000\n\$rate = 8.5\n\$years = 20\n\$draft = \"\" transient\n" +
            "root = Card(pad: l) [rate, kpi]\nrate = Slider(\$rate, min: 6, max: 14)\nkpi = Metric(\"Monthly EMI\", emi, format: \"inr\")\n" +
            "emi = monthly(\$principal, \$rate, \$years)\nfn monthly(p, r, y) = p * (r/1200) * pow(1 + r/1200, y*12) / (pow(1 + r/1200, y*12) - 1)\n" +
            "on change(\$rate) when \$rate > 10 -> !haptic(tick)\nexport emi, \$rate"
        assertEquals(Compiler.compile(src, BuiltinCatalog).print(), prog(json).print())
    }

    @Test fun `T-121 and T-122 expression and escaped strings`() {
        val a = Session(prog("""{"weave":1,"nodes":{"root":{"t":"Text","a":{"content":"=1+1"}}}}""")).root()!!
        assertEquals("2", com.echoflow.weave.core.eval.Formatter().auto(a.args["content"]!!))
        val b = Session(prog("""{"weave":1,"nodes":{"root":{"t":"Text","a":{"content":"'=1+1"}}}}""")).root()!!
        assertEquals(WStr("=1+1"), b.args["content"])
    }

    @Test fun `T-123 children name nodes or become Text`() {
        val p = prog("""{"weave":1,"nodes":{"root":{"t":"Column","c":["kpi","Hello"]},"kpi":{"t":"Text","a":{"content":"k"}}}}""")
        val root = Session(p).root()!!
        assertEquals(listOf("Text", "Text"), root.children.map { it.type })
        assertEquals(WStr("Hello"), root.children[1].args["content"])
    }

    @Test fun `T-124 truncated JSON still renders`() {
        val root = Session(prog("""{"weave":1,"nodes":{"root":{"t":"Text","a":{"content":"hi"}}""")).root()
        assertEquals(WStr("hi"), root!!.args["content"])
    }

    @Test fun `edits lower to edit bodies`() {
        val l = JsonLowering.lower("""{"edit":"loan","let":{"emi":"1"},"delete":["kpi"]}""")!!
        assertEquals("loan", l.editTarget)
        assertTrue(l.source.contains("emi = 1") && l.source.contains("-kpi"))
    }

    @Test fun `a cut-off literal is dropped, not completed`() {
        val o = com.echoflow.weave.core.json.TolerantJson.parse("{\"a\": 1, \"x\": tru") as com.echoflow.weave.core.json.TolerantJson.JObj
        assertTrue(o.fields.containsKey("a"))
        assertTrue(!o.fields.containsKey("x"))
    }

    @Test fun `the schema names every component and parses as JSON`() {
        val schema = com.echoflow.weave.core.prompt.SchemaGenerator.schema()
        val parsed = com.echoflow.weave.core.json.TolerantJson.parse(schema) as com.echoflow.weave.core.json.TolerantJson.JObj
        assertTrue(parsed.fields.containsKey("${'$'}defs"))
        for (c in listOf("Slider", "Metric", "Card")) assertTrue(c, schema.contains("\"const\":\"$c\""))
        // a Slider needs its value or a bind; a Card can't take `bind`; `do` is text or a text list
        val slider = schema.substringAfter("\"const\":\"Slider\"").substringBefore("\"const\":")
        assertTrue(slider, slider.contains("{\"required\":[\"bind\"]}"))
        val card = schema.substringAfter("\"const\":\"Card\"").substringBefore("\"const\":")
        assertTrue(card, !card.contains("\"bind\""))
        assertTrue(schema.contains("\"do\":{\"anyOf\":[{\"type\":\"string\"}"))
    }
}
