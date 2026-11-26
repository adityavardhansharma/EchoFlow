Here is a breakdown of your monthly expenses with a chart, sortable table, and total spend.

```weave
@weave 1 id: monthly_expenses title: "Monthly expenses"
root = Card(gap: m) [
  Title("Monthly expenses", subtitle: "5 categories · {totalSpend | inr} total", icon: "account_balance_wallet"),
  Stat [
    Metric("Monthly total", totalSpend, format: inr, tone: primary),
    Metric("Top category", topCat.amount, format: inr, prefix: "{topCat.category}: ", tone: tertiary)
  ],
  BarChart(expenses, x: "category", y: "amount", h: s),
  Table(rows, cols: [col("Category", "category"), col("Amount (₹)", "amount"), col("Share (%)", "sharePct")], sort: true)
]
expenses = [{category: "Rent", amount: 18000}, {category: "Food", amount: 9000}, {category: "Travel", amount: 4000}, {category: "Bills", amount: 3500}, {category: "Fun", amount: 5500}]
totalSpend = sum(map(expenses, e => e.amount))
topCat = first(sortBy(expenses, "amount", desc: true))
rows = map(expenses, e => {category: e.category, amount: e.amount, sharePct: round(e.amount * 100 / totalSpend, 1)})
```
