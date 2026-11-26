Tap any column header to sort the three laptops by name, weight, or price.

```weave
@weave 1 id: cmp_laptops title: "Laptop Comparison"
root = Card(gap: m) [
  Title("Laptop comparison", subtitle: "Tap a column header to sort", icon: "laptop_mac"),
  Stat [
    Metric("Lightest", "Air 13", suffix: " (1.24 kg)", tone: primary),
    Metric("Most affordable", "Book 15", suffix: " (₹69,999)", tone: success)
  ],
  Table(laptops, cols: [
    col("Laptop", "name"),
    col("Weight (kg)", "weight"),
    col("Price (₹)", "price")
  ], sort: true),
  Callout("Air 13 saves 0.36 kg over Book 15, while Book 15 costs ₹29,901 less.", kind: info)
]
laptops = [
  {name: "Air 13", weight: 1.24, price: 99900},
  {name: "Zen 14", weight: 1.39, price: 84990},
  {name: "Book 15", weight: 1.6, price: 69999}
]
```
