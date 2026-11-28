Move the slider to see how ₹10,000 at 10% a year grows with simple interest versus compound interest.

```weave
@weave 1 id: learn_interest title: "Simple vs Compound Interest"
$years = 10
root = Card(gap: m) [
  Title("Simple vs compound interest", subtitle: "₹10,000 principal at 10% per year for {$years} yrs", icon: "savings"),
  Slider($years, min: 1, max: 25, step: 1, label: "Number of years", suffix: " yrs"),
  Stat [
    Metric("Simple interest", simpleEnd, format: inr, tone: secondary),
    Metric("Compound interest", compEnd, format: inr, tone: primary),
    Metric("Compounding bonus", compEnd - simpleEnd, format: inr, tone: success)
  ],
  Chart(series, h: m) [
    line(x: "year", y: "compound", tone: primary, points: true),
    line(x: "year", y: "simple", tone: tertiary, points: true)
  ],
  Callout("Simple interest adds a flat ₹1,000 every year (10% of the original ₹10,000). Compound interest adds 10% of the growing balance—so in year {$years}, it earns {round(10000 * pow(1.1, $years - 1) * 0.1, 0) | inr} in a single year.", kind: tip)
]
allYears = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25]
simpleEnd = round(10000 * (1 + 0.1 * $years), 0)
compEnd = round(10000 * pow(1.1, $years), 0)
series = map(take(allYears, $years), y => {year: y, simple: round(10000 * (1 + 0.1 * y), 0), compound: round(10000 * pow(1.1, y), 0)})
on change($years) -> !haptic(tick)
```
