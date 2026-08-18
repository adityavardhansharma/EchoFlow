package dev.firecrawl.anydoc

/**
 * Parser anydoc will run. Container variants that share a parser
 * (`.docm`, `.xlsm`, `.ppsx`, …) map onto these.
 */
enum class Format(val wireName: String, val extensions: List<String>) {
    DOC("doc", listOf("doc")),
    DOCX("docx", listOf("docx", "docm")),
    ODT("odt", listOf("odt")),
    PDF("pdf", listOf("pdf")),
    PPT("ppt", listOf("ppt", "pps", "pot")),
    PPTX("pptx", listOf("pptx", "pptm", "ppsx", "ppsm")),
    RTF("rtf", listOf("rtf")),
    EPUB("epub", listOf("epub")),
    XLSX("xlsx", listOf("xlsx", "xlsm", "xlsb", "xls")),
    ODS("ods", listOf("ods")),
    ODP("odp", listOf("odp")),
    CSV("csv", listOf("csv"));

    companion object {
        val allExtensions: List<String> = entries.flatMap { it.extensions }

        fun fromWireName(name: String?): Format? {
            if (name.isNullOrBlank()) return null
            return entries.firstOrNull { it.wireName.equals(name, ignoreCase = true) }
        }

        fun fromExtension(extension: String?): Format? {
            if (extension.isNullOrBlank()) return null
            val ext = extension.trim().trimStart('.').lowercase()
            return entries.firstOrNull { ext in it.extensions }
        }
    }
}
