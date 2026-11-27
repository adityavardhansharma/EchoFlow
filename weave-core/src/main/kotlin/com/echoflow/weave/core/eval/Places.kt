package com.echoflow.weave.core.eval

/** Reading and writing places, 03 §5.4–5.5. */
object Places {

    /** Ordinary member/index access; missing → null. */
    fun read(root: WValue, path: List<WValue>): WValue {
        var v = root
        for (seg in path) v = access(v, seg)
        return v
    }

    fun access(v: WValue, seg: WValue): WValue = when {
        v == WNull -> WNull
        v is WRec && (seg is WStr || seg is WSym) -> v[key(seg)]
        v is WList && seg is WNum -> listIndex(v.items.size, seg.v)?.let { v.items[it] } ?: WNull
        // Pluck and length, the same as Evaluator.member: `items.name` and `items.len`.
        v is WList && (seg is WStr || seg is WSym) && key(seg) == "len" -> Builtins.len(v)
        v is WList && (seg is WStr || seg is WSym) -> WList(v.items.map { access(it, seg) })
        v is WStr && seg is WNum -> {
            val cps = v.v.codePoints().toArray()
            listIndex(cps.size, seg.v)?.let { WStr(String(Character.toChars(cps[it]))) } ?: WNull
        }
        else -> WNull
    }

    private fun key(seg: WValue) = when (seg) { is WStr -> seg.v; is WSym -> seg.name; else -> seg.toString() }

    /** Round half away from zero; null for NaN. */
    private fun halfUp(i: Double): Int? {
        if (i.isNaN()) return null
        val r = if (i >= 0) Math.floor(i + 0.5) else Math.ceil(i - 0.5)
        return r.toInt()
    }

    /** `i` rounded half-up; negative from the end; out of range → null. */
    fun listIndex(size: Int, i: Double): Int? {
        var k = halfUp(i) ?: return null
        if (k < 0) k += size
        return if (k in 0 until size) k else null
    }

    /**
     * Writes [value] at [path] under [root], copying only along the path. Returns null when the
     * write is ignored (`E205`): member on a non-record, list index out of range.
     */
    fun write(root: WValue, path: List<WValue>, value: WValue): WValue? {
        if (path.isEmpty()) return value
        val seg = path[0]
        val rest = path.subList(1, path.size)
        return when {
            root is WRec && (seg is WStr || seg is WSym) -> {
                val k = key(seg)
                val child = write(root[k], rest, value) ?: return null
                WRec(LinkedHashMap(root.fields).apply { put(k, child) })
            }
            root is WList && seg is WNum -> {
                val n = root.items.size
                var k = halfUp(seg.v) ?: return null
                if (k < 0) k += n
                when {
                    k in 0 until n -> {
                        val child = write(root.items[k], rest, value) ?: return null
                        WList(root.items.toMutableList().apply { set(k, child) })
                    }
                    k == n -> {
                        val child = write(WNull, rest, value) ?: return null
                        WList(root.items + child)
                    }
                    else -> null
                }
            }
            root == WNull && rest.isEmpty() && (seg is WStr || seg is WSym) -> null
            else -> null
        }
    }
}
