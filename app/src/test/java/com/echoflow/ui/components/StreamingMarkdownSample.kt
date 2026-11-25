package com.echoflow.ui.components

/** A long mixed GFM + math reply, as a model would stream it. */
internal val STREAMING_SAMPLE: String = (1..8).joinToString("\n\n") { section -> sampleSection(section) }

/** Splits [text] the way a model streams it: words and whitespace runs, about four characters at a time. */
internal fun streamTokens(text: String): List<String> {
    val tokens = ArrayList<String>()
    var i = 0
    while (i < text.length) {
        var end = i + 1
        while (end < text.length && end - i < 4 && text[end].isWhitespace() == text[i].isWhitespace()) end++
        tokens += text.substring(i, end)
        i = end
    }
    return tokens
}

private fun sampleSection(n: Int): String = """
## ${n}. Pricing and growth

Plans cost ${'$'}5 and ${'$'}10 a month, or ${'$'}100 a year. With rate ${'$'}r = 0.05${'$'} and principal ${'$'}P${'$'}, the balance after ${'$'}t${'$'} years is \(P(1+r)^t\), so **compound interest** grows *faster* than ~~simple~~ interest. See [the docs](https://example.com/docs?a=${'$'}x) or https://example.com/raw.

${'$'}${'$'}
A = P\left(1 + \frac{r}{n}\right)^{nt}
${'$'}${'$'}

| Plan | Monthly | Yearly |
|:-----|-------:|:------:|
| Basic | ${'$'}5 | ${'$'}50 |
| Pro | ${'$'}10 | ${'$'}100 |
| Team | ${'$'}25 | ${'$'}250 |

1. First, measure the baseline.
2. Then compare **two** variants:
   - variant A with ${'$'}\alpha = 0.1${'$'}
   - variant B with ${'$'}\beta = 0.2${'$'}

3. Finally, ship the winner.

- [x] Write the model
- [ ] Review the numbers

> Note: the quadratic formula is \[x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}\] and it has two roots.

```kotlin
fun balance(p: Double, r: Double, t: Int): Double {
    val price = "${'$'}5"

    return p * Math.pow(1 + r, t.toDouble())
}
```

\[
\sum_{k=1}^{n} k = \frac{n(n+1)}{2}
\]

Inline code like `val cost = ${'$'}10` stays code, and ![chart](https://example.com/chart.png) is an image.

---

Use <b>bold</b>, <i>italic</i>, and <mark>highlight</mark> where it helps.<br>A new line follows.
""".trim()
