Here is an interactive 50/30/20 monthly budget planner starting at a ₹60,000 salary.

```weave
@weave 1 id: budget_50_30_20 title: "50/30/20 Budget"
$salary = 60000
root = Card(gap: m) [
  Title("50/30/20 monthly budget", subtitle: "For a {$salary | inr} take-home salary", icon: "pie_chart"),
  Slider($salary, min: 15000, max: 300000, step: 2500, label: "Monthly salary", format: inr),
  Stat [
    Metric("Needs (50%)", needs, format: inr, tone: primary),
    Metric("Wants (30%)", wants, format: inr, tone: tertiary),
    Metric("Savings (20%)", savings, format: inr, tone: success)
  ],
  BarChart(buckets, x: "name", y: "amount", h: s),
  List(buckets, render: b => Row(justify: between, align: center) [
    Column(gap: xxs, w: 2/3) [
      Text(b.name, style: titleSmall),
      Text(b.examples, style: bodySmall, tone: muted)
    ],
    Text("{b.amount | inr}", style: titleMedium, tone: primary)
  ])
]
needs = $salary * 0.50
wants = $salary * 0.30
savings = $salary * 0.20
buckets = [{name: "Needs (50%)", amount: round(needs, 0), examples: "Rent, groceries, bills, commute"}, {name: "Wants (30%)", amount: round(wants, 0), examples: "Dining, travel, shopping, hobbies"}, {name: "Savings (20%)", amount: round(savings, 0), examples: "Emergency fund, SIPs, debt payoff"}]
on change($salary) -> !haptic(tick)
```
