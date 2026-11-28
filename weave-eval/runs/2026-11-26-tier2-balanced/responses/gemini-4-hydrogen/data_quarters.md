Here is your quarterly sales bar chart along with the yearly total in lakh rupees.

```weave
@weave 1 id: quarterly_sales title: "Quarterly sales"
root = Card(gap: m) [
  Title("Quarterly sales", subtitle: "Values in ₹ lakh", icon: "bar_chart"),
  Stat [
    Metric("Yearly total", yearlyTotal, format: int, prefix: "₹", suffix: "L", tone: primary),
    Metric("Quarterly avg", avgSales, format: dec(1), prefix: "₹", suffix: "L", tone: secondary),
    Metric("Best quarter", bestQ.lakh, format: int, prefix: "{bestQ.quarter}: ₹", suffix: "L", tone: success)
  ],
  BarChart(sales, x: "quarter", y: "lakh", h: m)
]
sales = [{quarter: "Q1", lakh: 120}, {quarter: "Q2", lakh: 150}, {quarter: "Q3", lakh: 90}, {quarter: "Q4", lakh: 200}]
yearlyTotal = sum(map(sales, s => s.lakh))
avgSales = avg(map(sales, s => s.lakh))
bestQ = first(sortBy(sales, "lakh", desc: true))
```
