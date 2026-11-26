Compare how much monthly cashback each card gives as you adjust your spend.

```weave
@weave 1 id: cmp_cards title: "Credit Card Cashback"
$spend = 20000
root = Card(gap: m) [
  Title("Credit card cashback", subtitle: "Monthly spend: {$spend | inr}", icon: "credit_card"),
  Slider($spend, min: 2000, max: 100000, step: 1000, label: "Monthly spend", prefix: "₹"),
  Stat [
    Metric("Card A (1%)", cbA, format: inr, tone: secondary),
    Metric("Card B (2% cap ₹500)", cbB, format: inr, tone: cbB >= cbC ? primary : neutral),
    Metric("Card C (1.5%)", cbC, format: inr, tone: cbC > cbB ? primary : neutral)
  ],
  Chart(rows, h: s) [
    bar(x: "card", y: "cashback", tone: primary, labels: true)
  ],
  Callout("{bestCard} earns the most at {bestAmt | inr}/mo ({bestAmt * 12 | inr}/yr). Card B caps out at ₹25,000 spend and Card C overtakes it above ₹33,333.", kind: tip)
]
cbA = round($spend * 0.01, 0)
cbB = round(min($spend * 0.02, 500), 0)
cbC = round($spend * 0.015, 0)
bestAmt = max(cbA, max(cbB, cbC))
bestCard = cbB >= cbC and cbB >= cbA ? "Card B" : (cbC >= cbA ? "Card C" : "Card A")
rows = [
  {card: "Card A (1%)", cashback: cbA},
  {card: "Card B (2% cap)", cashback: cbB},
  {card: "Card C (1.5%)", cashback: cbC}
]
on change($spend) -> !haptic(tick)
```
