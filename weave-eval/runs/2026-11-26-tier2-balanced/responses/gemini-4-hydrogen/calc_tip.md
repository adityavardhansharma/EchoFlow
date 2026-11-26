Here is an interactive tip splitter starting with a ₹2,400 bill, 10% tip, and 4 people.

```weave
@weave 1 id: tip_splitter title: "Tip splitter"
$bill = 2400
$tipPct = 10
$people = 4
root = Card(gap: m) [
  Title("Tip splitter", subtitle: "{$bill | inr} + {$tipPct}% tip · {$people} people", icon: "payments"),
  Slider($bill, min: 200, max: 20000, step: 50, label: "Bill amount", format: inr),
  Segmented($tipPct, options: [0, 5, 10, 15, 20], suffix: "%"),
  Slider($people, min: 1, max: 12, step: 1, label: "People"),
  Stat [
    Metric("Per person", perPerson, format: inr, tone: primary),
    Metric("Tip amount", tipAmount, format: inr, tone: tertiary),
    Metric("Total bill", totalBill, format: inr, tone: secondary)
  ]
]
tipAmount = $bill * $tipPct / 100
totalBill = $bill + tipAmount
perPerson = totalBill / $people
on change($people) -> !haptic(tick)
on change($tipPct) -> !haptic(tick)
```
