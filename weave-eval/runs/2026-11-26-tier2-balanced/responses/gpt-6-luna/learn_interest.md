Adjust the time period to compare simple and annually compounded interest.

```weave
@weave 1 id: interest_lesson title: "Simple vs compound interest"
$years = 5
root = Card(gap: m) [
  Title("₹10,000 at 10% per year", subtitle: "Simple interest adds the same amount yearly; compound interest earns interest on past interest."),
  Slider($years, min: 1, max: 30, step: 1, label: "Time", suffix: " years"),
  Stat [
    Metric("Simple interest total", 10000 * (1 + 0.10 * $years), format: inr, tone: primary),
    Metric("Compound interest total", 10000 * pow(1.10, $years), format: inr, tone: tertiary)
  ],
  Text("Simple total = principal × (1 + rate × years). Compound total assumes annual compounding.")
]
```
