package com.echoflow.weave.core.check

/** Declarable state types, 02 §4.1 `type_name`. */
enum class WType(val keyword: String) {
    NUM("num"), STR("str"), BOOL("bool"), LIST("list"), REC("rec"), DUR("dur"),
    DATE("date"), TIME("time"), DATETIME("datetime"), ANY("any");

    companion object {
        fun of(k: String) = entries.firstOrNull { it.keyword == k }
    }
}

/** Token sets, 04 §1. */
enum class TokenSet { SPACE, HEIGHT, WIDTH, TONE, EMPHASIS, COLOR, TEXT, SHAPE, ELEV, ICON, HAPTIC, ENUM }

/**
 * Slot types used in the catalog, 03 §2.2. [Enum] is an inline enumeration (`filled|tonal`).
 */
sealed interface TypeSpec {
    val display: String

    data class Simple(val name: String) : TypeSpec {
        override val display get() = name
    }

    data class Tok(val set: TokenSet) : TypeSpec {
        override val display get() = "token<${set.name.lowercase()}>"
    }

    data class Enum(val values: List<String>) : TypeSpec {
        override val display get() = values.joinToString("|")
    }

    data class Union(val options: List<TypeSpec>) : TypeSpec {
        override val display get() = options.joinToString(" | ") { it.display }
    }

    companion object {
        val ANY = Simple("any")
        val NUM = Simple("num")
        val STR = Simple("str")
        val BOOL = Simple("bool")
        val DUR = Simple("dur")
        val DATE = Simple("date")
        val LIST = Simple("list")
        val REC = Simple("rec")
        val NODE = Simple("node")
        val NODES = Simple("nodes")
        val ACTION = Simple("action")
        val FN = Simple("fn")
        val FIELD = Simple("field")
        val STATE = Simple("state")
        val NAME = Simple("name")
        val COLOR = Tok(TokenSet.COLOR)
        val SPACE = Tok(TokenSet.SPACE)
        val HEIGHT = Tok(TokenSet.HEIGHT)
        val WIDTH = Tok(TokenSet.WIDTH)
        val TONE = Tok(TokenSet.TONE)
        val TEXT = Tok(TokenSet.TEXT)
        val SHAPE = Tok(TokenSet.SHAPE)
        val ICON = Tok(TokenSet.ICON)
        val HAPTIC = Tok(TokenSet.HAPTIC)
        fun enumOf(vararg v: String) = Enum(v.toList())
    }
}

/** True when a slot takes a token (so bare words there are tokens, not unresolved names). */
fun TypeSpec.isTokenSlot(): Boolean = when (this) {
    is TypeSpec.Tok, is TypeSpec.Enum -> true
    is TypeSpec.Union -> options.any { it.isTokenSlot() }
    else -> false
}

fun TypeSpec.isColorSlot(): Boolean = when (this) {
    is TypeSpec.Tok -> set == TokenSet.COLOR
    is TypeSpec.Union -> options.any { it.isColorSlot() }
    else -> false
}
