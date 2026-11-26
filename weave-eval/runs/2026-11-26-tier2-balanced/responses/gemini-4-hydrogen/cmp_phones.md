Here is a table and chart comparing the Pixel 9, Galaxy S25, and iPhone 16 on price and battery capacity.

```weave
@weave 1 id: cmp_phones title: "Phone Comparison"
$metric = "battery"
root = Card(gap: m) [
  Title("Pixel 9 vs Galaxy S25 vs iPhone 16", subtitle: "Price and battery capacity", icon: "phone_android"),
  Segmented($metric, options: ["battery", "price"], labels: ["Battery (mAh)", "Price (₹)"]),
  Chart(phones, h: s) [
    bar(x: "name", y: $metric, tone: primary, labels: true)
  ],
  Table(phones, cols: [
    col("Phone", "name"),
    col("Price (₹)", "price"),
    col("Battery (mAh)", "battery"),
    col("mAh / ₹1,000", "efficiency")
  ], sort: true),
  Callout("All three sit around ₹80,000, while Pixel 9 packs 21% more battery than Galaxy S25 and 32% more than iPhone 16.", kind: info)
]
phones = [
  {name: "Pixel 9", price: 79999, battery: 4700, efficiency: round(4700 / 79.999, 1)},
  {name: "Galaxy S25", price: 80999, battery: 3900, efficiency: round(3900 / 80.999, 1)},
  {name: "iPhone 16", price: 79900, battery: 3561, efficiency: round(3561 / 79.9, 1)}
]
on change($metric) -> !haptic(tick)
```
