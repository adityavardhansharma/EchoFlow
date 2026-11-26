Set your timeline to see the monthly saving target.
```weave
@weave 1 id: savings title: "Savings goal"
$months = 12
root = Card(gap: m) [Title("Save ₹3,00,000"), Slider($months, min: 1, max: 60, step: 1, label: "Months"), Metric("Save each month", 300000 / $months, format: inr)]
```
