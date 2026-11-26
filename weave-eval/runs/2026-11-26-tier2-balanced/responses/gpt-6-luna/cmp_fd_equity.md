Explore how the two yearly compounded investments grow over time.

```weave
@weave 1 id: fd_equity title: "FD vs equity"
$years = 5
root = Card(gap: m) [
  Title("₹1,00,000 invested", subtitle: "Annual compounding; returns are illustrative"),
  Slider($years, min: 1, max: 30, step: 1, label: "Years", suffix: " years"),
  Stat [
    Metric("FD · 7%", 100000 * pow(1.07, $years), format: inr, tone: primary),
    Metric("Equity · 12%", 100000 * pow(1.12, $years), format: inr, tone: tertiary)
  ],
  Text("Equity returns are not guaranteed; actual results can be higher or lower.")
]
```
