package com.echoflow.weave.core.eval

/** Finance (03 §10.7) and unit conversion (03 §10.8). */
object Finance {
    fun pmt(rate: Double, n: Double, pv: Double): Double =
        if (rate == 0.0) pv / n else pv * rate / (1 - Math.pow(1 + rate, -n))

    fun npv(rate: Double, flows: List<Double>): Double =
        flows.withIndex().sumOf { (i, f) -> f / Math.pow(1 + rate, i.toDouble()) }

    private fun dnpv(rate: Double, flows: List<Double>): Double =
        flows.withIndex().sumOf { (i, f) -> -i * f / Math.pow(1 + rate, i + 1.0) }

    /** Newton–Raphson from 0.1 (50 iterations, 1e-7), then bisection on [−0.9999, 10]. */
    fun irr(flows: List<Double>): Double? {
        if (flows.size < 2) return null
        var r = 0.1
        for (k in 0 until 50) {
            val v = npv(r, flows)
            val d = dnpv(r, flows)
            if (d == 0.0 || d.isNaN()) break
            val next = r - v / d
            if (next.isNaN() || next.isInfinite()) break
            if (Math.abs(next - r) < 1e-7) return next
            r = next
        }
        var lo = -0.9999
        var hi = 10.0
        var flo = npv(lo, flows)
        val fhi = npv(hi, flows)
        if (flo.isNaN() || fhi.isNaN() || flo * fhi > 0) return null
        for (k in 0 until 200) {
            val mid = (lo + hi) / 2
            val fm = npv(mid, flows)
            if (Math.abs(fm) < 1e-12 || (hi - lo) / 2 < 1e-12) return mid
            if (fm * flo < 0) hi = mid else { lo = mid; flo = fm }
        }
        return (lo + hi) / 2
    }

    /**
     * One row per year: amounts paid during that year and the year-end balance. Monthly schedule
     * with `emi`; balances rounded to 2 decimals per month; the last payment clears the balance.
     */
    fun amortize(principal: Double, annualRate: Double, years: Double): WValue {
        val months = Math.round(years * 12).toInt()
        val r = annualRate / 12
        val pay = pmt(r, months.toDouble(), principal)
        var bal = principal
        val rows = ArrayList<WValue>()
        var yp = 0.0
        var yi = 0.0
        for (m in 1..months) {
            val interest = bal * r
            // principal paid is measured against the rounded balance, so the yearly sums add up
            // to exactly the loan; the last payment clears whatever is left
            val next = if (m == months) 0.0 else Math.max(0.0, Math.round((bal - (pay - interest)) * 100) / 100.0)
            val princ = bal - next
            bal = next
            yp += princ
            yi += interest
            if (m % 12 == 0 || m == months) {
                rows += WRec.of(
                    "year" to WNum.of(Math.ceil(m / 12.0)), "principal" to WNum.of(yp),
                    "interest" to WNum.of(yi), "balance" to WNum.of(bal),
                )
                yp = 0.0; yi = 0.0
            }
        }
        return WList(rows)
    }
}

object Units {
    private val tables: List<Map<String, Double>> = listOf(
        mapOf("m" to 1.0, "km" to 1000.0, "cm" to 0.01, "mm" to 0.001, "mi" to 1609.344, "yd" to 0.9144, "ft" to 0.3048, "in" to 0.0254),
        mapOf("kg" to 1.0, "g" to 0.001, "mg" to 1e-6, "lb" to 0.45359237, "oz" to 0.028349523125, "t" to 1000.0),
        mapOf("l" to 1.0, "ml" to 0.001, "gal" to 3.785411784, "qt" to 0.946352946, "cup" to 0.236588, "tbsp" to 0.01478676478125, "tsp" to 0.00492892159375, "floz" to 0.0295735295625),
        mapOf("mps" to 1.0, "kmh" to 1 / 3.6, "mph" to 0.44704, "kn" to 1852.0 / 3600.0),
        mapOf("B" to 1.0, "KB" to 1e3, "MB" to 1e6, "GB" to 1e9, "TB" to 1e12, "KiB" to 1024.0, "MiB" to 1048576.0, "GiB" to 1073741824.0),
        mapOf("ms" to 0.001, "s" to 1.0, "min" to 60.0, "h" to 3600.0, "day" to 86400.0, "week" to 604800.0),
        mapOf("m2" to 1.0, "km2" to 1e6, "ft2" to 0.09290304, "acre" to 4046.8564224, "ha" to 1e4),
    )

    fun convert(v: Double, from: String, to: String): Double? {
        val temps = setOf("C", "F", "K")
        if (from in temps || to in temps) {
            if (from !in temps || to !in temps) return null
            val c = when (from) { "C" -> v; "F" -> (v - 32) * 5 / 9; else -> v - 273.15 }
            return when (to) { "C" -> c; "F" -> c * 9 / 5 + 32; else -> c + 273.15 }
        }
        val t = tables.firstOrNull { from in it && to in it } ?: return null
        return v * t.getValue(from) / t.getValue(to)
    }
}
