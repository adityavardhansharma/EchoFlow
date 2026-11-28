Yearly sales total **₹560 lakh**.
```weave
@weave 1 id: sales title: "Quarterly sales"
$data = [{quarter: "Q1", sales: 120}, {quarter: "Q2", sales: 150}, {quarter: "Q3", sales: 90}, {quarter: "Q4", sales: 200}]
root = Card(gap: m) [Title("Sales (₹ lakh)"), BarChart($data, x: "quarter", y: "sales"), Metric("Year total", sum(map($data, x => x.sales)), format: dec(0), suffix: " lakh")]
```
