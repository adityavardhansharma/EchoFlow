Compare the monthly cashback as you adjust your spend.

```weave
@weave 1 id: card_cashback title: "Credit card cashback"
$spend = 10000
root = Card(gap: m) [
  Title("Monthly cashback", subtitle: "Compare rewards by monthly spend"),
  Slider($spend, min: 0, max: 100000, step: 1000, label: "Monthly spend", format: inr),
  Stat [
    Metric("Card A · 1%", $spend * 0.01, format: inr, tone: secondary),
    Metric("Card B · 2%, capped at ₹500", min($spend * 0.02, 500), format: inr, tone: primary),
    Metric("Card C · 1.5%", $spend * 0.015, format: inr, tone: tertiary)
  ]
]
```
