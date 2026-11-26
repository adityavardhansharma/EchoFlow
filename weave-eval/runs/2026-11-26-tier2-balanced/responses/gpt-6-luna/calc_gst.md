Change the pre-tax amount to recalculate GST.
```weave
@weave 1 id: gst title: "GST calculator"
$amount = 1000
root = Card(gap: m) [Title("18% GST"), Slider($amount, min: 100, max: 100000, label: "Amount", format: inr), Metric("GST", $amount * 18%, format: inr), Metric("Total", $amount * 1.18, format: inr)]
```
