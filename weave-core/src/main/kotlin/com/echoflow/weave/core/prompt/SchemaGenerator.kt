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
            // `bind` is lowered onto the state lead, so only components with one may use it
            val names = (c.allParams.map { it.name } + common + listOfNotNull("bind".takeIf { c.bindsState })).distinct()
            val props = names.joinToString(",") { "${q(it)}:{}" }
            // required parameters, with `bind` accepted in place of the state lead
            val lead = c.leads.firstOrNull()?.takeIf { c.bindsState && it.required }?.name
            val required = c.allParams.filter { it.required && it.name != lead }.map { it.name }
            val reqJson = if (required.isEmpty()) "" else ""","required":[${required.joinToString(",") { q(it) }}]"""
            val leadJson = if (lead == null) "" else ""","anyOf":[{"required":[${q(lead)}]},{"required":["bind"]}]"""
            val needsA = required.isNotEmpty() || lead != null
            """{"type":"object","properties":{"t":{"const":${q(c.name)}},"a":{"type":"object","properties":{$props},"additionalProperties":false$reqJson$leadJson},""" +
                """"c":{"type":"array","items":{"anyOf":[{"type":"string"},{"${'$'}ref":"#/${'$'}defs/node"}]}}},"required":["t"${if (needsA) ",\"a\"" else ""}]}"""
        }
        return """{"${'$'}schema":"https://json-schema.org/draft/2020-12/schema","type":"object",""" +
            """"properties":{"weave":{"const":1},"edit":{"type":"string"},"id":{"type":"string"},"title":{"type":"string"},""" +
            """"size":{"enum":["inline","card","wide","full"]},"icon":{"type":"string"},"keep":{"type":"boolean"},""" +
            """"state":{"type":"object"},"let":{"type":"object","additionalProperties":{"type":"string"}},""" +
            """"fn":{"type":"object"},"nodes":{"type":"object","additionalProperties":{"${'$'}ref":"#/${'$'}defs/node"}},""" +
            """"on":{"type":"array","items":{"type":"object","properties":{"trigger":{"type":"string"},"when":{"type":"string"},"do":{"anyOf":[{"type":"string"},{"type":"array","items":{"type":"string"}}]}},"required":["trigger","do"]}},""" +
            """"export":{"type":"array","items":{"type":"string"}},"delete":{"type":"array","items":{"type":"string"}}},""" +
            """"${'$'}defs":{"node":{"anyOf":[$nodeVariants]}}}"""
    }
}
