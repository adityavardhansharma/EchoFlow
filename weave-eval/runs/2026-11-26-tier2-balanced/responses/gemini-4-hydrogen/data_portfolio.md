Here is your portfolio breakdown showing the allocation chart and rupee amounts for ₹10,00,000.

```weave
@weave 1 id: portfolio_split title: "Portfolio allocation"
$total = 1000000
root = Card(gap: m) [
  Title("Portfolio breakdown", subtitle: "Total invested: {$total | inr}", icon: "pie_chart"),
  Chart(rows, polar: true, h: s) [
    arc(value: "amount", label: "asset")
  ],
  Table(rows, cols: [col("Asset", "asset"), col("Weight", "weight"), col("Amount (₹)", "amount")], sort: true),
  Slider($total, min: 100000, max: 5000000, step: 50000, label: "Portfolio value", format: inr)
]
alloc = [{asset: "Equity", pct: 60}, {asset: "Debt", pct: 25}, {asset: "Gold", pct: 10}, {asset: "Cash", pct: 5}]
rows = map(alloc, a => {asset: a.asset, weight: "{a.pct}%", amount: round($total * a.pct / 100, 0)})
on change($total) -> !haptic(tick)
```
