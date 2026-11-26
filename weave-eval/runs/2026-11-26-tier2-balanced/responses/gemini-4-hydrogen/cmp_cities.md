Here is the cost breakdown and total across Bengaluru, Pune, and Hyderabad.

```weave
@weave 1 id: cmp_cities title: "City Cost Comparison"
$period = "monthly"
root = Card(gap: m) [
  Title("Monthly living costs", subtitle: "Rent, food, and transport totals", icon: "location_city"),
  Segmented($period, options: ["monthly", "annual"], labels: ["Monthly", "Annual"]),
  Stat [
    Metric("Bengaluru", 44000 * mult, format: inr, tone: error),
    Metric("Pune", 33000 * mult, format: inr, tone: secondary),
    Metric("Hyderabad", 31500 * mult, format: inr, tone: success)
  ],
  Chart(rows, h: m) [
    bar(x: "city", y: "rent", tone: primary, stack: true),
    bar(x: "city", y: "food", tone: secondary, stack: true),
    bar(x: "city", y: "transport", tone: tertiary, stack: true)
  ],
  Table(rows, cols: [
    col("City", "city"),
    col("Rent", "rent"),
    col("Food", "food"),
    col("Transport", "transport"),
    col("Total", "total")
  ], sort: true),
  Callout("Hyderabad is the lowest at ₹31,500/mo—saving ₹1,500/mo over Pune and ₹12,500/mo over Bengaluru.", kind: info)
]
mult = $period == "annual" ? 12 : 1
rows = [
  {city: "Bengaluru", rent: 28000 * mult, food: 12000 * mult, transport: 4000 * mult, total: 44000 * mult},
  {city: "Pune", rent: 20000 * mult, food: 10000 * mult, transport: 3000 * mult, total: 33000 * mult},
  {city: "Hyderabad", rent: 18000 * mult, food: 10000 * mult, transport: 3500 * mult, total: 31500 * mult}
]
on change($period) -> !haptic(tick)
```
