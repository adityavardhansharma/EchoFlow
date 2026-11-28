Set the term to estimate the maturity value (quarterly compounding).
```weave
@weave 1 id: fd title: "Fixed deposit"
$years = 5
root = Card(gap: m) [Title("Fixed deposit"), Slider($years, min: 1, max: 20, label: "Years"), Metric("Maturity amount", 500000 * pow(1 + 6.5% / 4, 4 * $years), format: inr)]
```
