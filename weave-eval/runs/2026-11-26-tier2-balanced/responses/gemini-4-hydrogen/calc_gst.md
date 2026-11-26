Here is a GST calculator starting at ₹1,000 before tax with 18% GST.

```weave
@weave 1 id: gst_calc title: "GST calculator"
$amount = 1000
$rate = 18
root = Card(gap: m) [
  Title("GST calculator", subtitle: "Base {$amount | inr} at {$rate}% GST", icon: "receipt_long"),
  Slider($amount, min: 100, max: 50000, step: 50, label: "Amount before tax", format: inr),
  Segmented($rate, options: [5, 12, 18, 28], suffix: "%"),
  Stat [
    Metric("GST amount", tax, format: inr, tone: tertiary),
    Metric("Total with GST", total, format: inr, tone: primary)
  ],
  Text("CGST ({$rate / 2}%): **{halfTax | inr}** · SGST ({$rate / 2}%): **{halfTax | inr}**", style: bodySmall, tone: muted)
]
tax = $amount * $rate / 100
total = $amount + tax
halfTax = tax / 2
on change($rate) -> !haptic(tick)
```
