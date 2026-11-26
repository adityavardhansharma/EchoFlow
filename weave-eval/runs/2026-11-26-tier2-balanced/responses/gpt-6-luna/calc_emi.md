Adjust the rate to estimate your monthly payment.
```weave
@weave 1 id: emi title: "Home loan EMI"
$rate = 8
root = Card(gap: m) [Title("Home loan"), Slider($rate, min: 5, max: 15, step: 0.1, label: "Annual interest", suffix: "%"), Metric("Monthly EMI", emi(5000000, $rate / 100, 20), format: inr)]
```
