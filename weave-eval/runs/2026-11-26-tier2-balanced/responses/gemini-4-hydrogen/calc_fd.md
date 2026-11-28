Here is your fixed deposit calculator for ₹5,00,000 at 6.5% p.a. compounded quarterly.

```weave
@weave 1 id: fd_calc title: "Fixed deposit"
$principal = 500000
$rate = 6.5
$years = 5
root = Card(gap: m) [
  Title("Fixed deposit calculator", subtitle: "{$principal | inr} · {$rate}% p.a. quarterly", icon: "savings"),
  Slider($years, min: 1, max: 15, step: 1, label: "Tenure", suffix: " yrs"),
  Slider($rate, min: 4, max: 10, step: 0.1, label: "Interest rate", suffix: "%"),
  Stat [
    Metric("Maturity amount", maturity, format: inr, tone: primary),
    Metric("Interest earned", interest, format: inr, tone: success)
  ],
  BarChart(summary, x: "part", y: "amount", h: s),
  Text("Effective annual yield: **{effectiveYield | num(2)}%** with quarterly compounding.", style: bodySmall, tone: muted)
]
maturity = $principal * pow(1 + ($rate / 100) / 4, 4 * $years)
interest = maturity - $principal
effectiveYield = (pow(1 + ($rate / 100) / 4, 4) - 1) * 100
summary = [{part: "Principal", amount: round($principal, 0)}, {part: "Interest", amount: round(interest, 0)}, {part: "Maturity", amount: round(maturity, 0)}]
on change($years) -> !haptic(tick)
```
