Adjust the investment period to see how your money grows.
```weave
@weave 1 id: compound title: "Compound interest"
$years = 10
root = Card(gap: m) [Title("Investment growth"), Slider($years, min: 1, max: 30, label: "Years"), Metric("Future value", compound(100000, 7%, $years), format: inr)]
```
