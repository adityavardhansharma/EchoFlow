Here is an interactive compound interest calculator set to ₹1,00,000 at 7% p.a. compounded yearly.

```weave
@weave 1 id: compound_calc title: "Compound interest"
$principal = 100000
$rate = 7
$years = 10
root = Card(gap: m) [
  Title("Compound interest", subtitle: "{$principal | inr} at {$rate}% yearly", icon: "trending_up"),
  Slider($years, min: 1, max: 30, step: 1, label: "Duration", suffix: " yrs"),
  Slider($principal, min: 10000, max: 1000000, step: 10000, label: "Principal", format: inr),
  Slider($rate, min: 2, max: 15, step: 0.5, label: "Annual rate", suffix: "%"),
  Stat [
    Metric("Maturity value", maturity, format: inr, tone: primary),
    Metric("Total interest", interest, format: inr, tone: success)
  ],
  BarChart(breakdown, x: "part", y: "amount", h: s),
  Text("Your money grows **{growthMultiple | num(2)}×** in **{$years} years**.", style: bodySmall, tone: muted)
]
maturity = $principal * pow(1 + $rate / 100, $years)
interest = maturity - $principal
growthMultiple = maturity / $principal
breakdown = [{part: "Principal", amount: round($principal, 0)}, {part: "Interest", amount: round(interest, 0)}, {part: "Maturity", amount: round(maturity, 0)}]
on change($years) -> !haptic(tick)
```
