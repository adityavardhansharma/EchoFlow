package com.echoflow.weave.core.prompt

import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.catalog.Catalog
import com.echoflow.weave.core.eval.Formatter

/**
 * The JSON Schema for Weave-JSON (02 §12.4, 05 §12), generated from the catalog: `t` is limited
 * to component names and each component's `a` to its parameters (plus the common ones). Passed as
 * `response_format` only for hidden requests to providers that support it.
 */
object SchemaGenerator {
    private fun q(s: String) = Formatter.jsonString(s)

    fun schema(catalog: Catalog = BuiltinCatalog.forBuild(2)): String {
        val comps = catalog.components
        val common = BuiltinCatalog.COMMON_NAMES
        val nodeVariants = comps.joinToString(",") { c ->
            val props = (c.allParams.map { it.name } + common + listOf("bind")).distinct().joinToString(",") { "${q(it)}:{}" }
            """{"type":"object","properties":{"t":{"const":${q(c.name)}},"a":{"type":"object","properties":{$props},"additionalProperties":false},"c":{"type":"array"}},"required":["t"]}"""
        }
        return """{"${'$'}schema":"https://json-schema.org/draft/2020-12/schema","type":"object",""" +
            """"properties":{"weave":{"const":1},"edit":{"type":"string"},"id":{"type":"string"},"title":{"type":"string"},""" +
            """"size":{"enum":["inline","card","wide","full"]},"icon":{"type":"string"},"keep":{"type":"boolean"},""" +
            """"state":{"type":"object"},"let":{"type":"object","additionalProperties":{"type":"string"}},""" +
            """"fn":{"type":"object"},"nodes":{"type":"object","additionalProperties":{"${'$'}ref":"#/${'$'}defs/node"}},""" +
            """"on":{"type":"array","items":{"type":"object","properties":{"trigger":{"type":"string"},"when":{"type":"string"},"do":{}},"required":["trigger","do"]}},""" +
            """"export":{"type":"array","items":{"type":"string"}},"delete":{"type":"array","items":{"type":"string"}}},""" +
            """"${'$'}defs":{"node":{"anyOf":[$nodeVariants]}}}"""
    }
}
