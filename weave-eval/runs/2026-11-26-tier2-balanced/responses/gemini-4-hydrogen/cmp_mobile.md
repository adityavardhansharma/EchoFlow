Here is how the three mobile plans compare by daily cost and 365-day equivalent spend.

```weave
@weave 1 id: cmp_mobile title: "Mobile Plan Comparison"
root = Card(gap: m) [
  Title("Mobile plans by cost per day", subtitle: "Effective daily & annualised rate", icon: "smartphone"),
  Stat [
    Metric("Plan A (28d)", 299 / 28, format: num(2), prefix: "₹", suffix: "/day", tone: error),
    Metric("Plan B (84d)", 719 / 84, format: num(2), prefix: "₹", suffix: "/day", tone: secondary),
    Metric("Plan C (365d)", 2999 / 365, format: num(2), prefix: "₹", suffix: "/day", tone: success)
  ],
  Chart(plans, h: s) [
    bar(x: "plan", y: "perDay", tone: primary, labels: true)
  ],
  Table(plans, cols: [
    col("Plan", "plan"),
    col("Price (₹)", "price"),
    col("Days", "days"),
    col("₹ / Day", "perDay"),
    col("365d Cost (₹)", "annual")
  ], sort: true),
  Callout("Plan C is the best value at ₹8.22/day—saving ₹899 a year compared to recharging Plan A every 28 days.", kind: tip)
]
plans = [
  {plan: "Plan A", price: 299, days: 28, perDay: round(299 / 28, 2), annual: round(299 / 28 * 365, 0)},
  {plan: "Plan B", price: 719, days: 84, perDay: round(719 / 84, 2), annual: round(719 / 84 * 365, 0)},
  {plan: "Plan C", price: 2999, days: 365, perDay: round(2999 / 365, 2), annual: 2999}
]
```
