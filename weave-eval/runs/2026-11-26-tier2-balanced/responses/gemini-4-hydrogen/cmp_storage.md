Here is how the three cloud storage plans compare by monthly price per GB.

```weave
@weave 1 id: cmp_storage title: "Cloud Storage Cost per GB"
root = Card(gap: m) [
  Title("Cloud storage by ₹/GB/month", subtitle: "Using 2 TB = 2,000 GB", icon: "cloud"),
  Stat [
    Metric("100 GB", 130 / 100, format: num(2), prefix: "₹", suffix: "/GB", tone: error),
    Metric("200 GB", 210 / 200, format: num(2), prefix: "₹", suffix: "/GB", tone: secondary),
    Metric("2 TB", 650 / 2000, format: num(2), prefix: "₹", suffix: "/GB", tone: success)
  ],
  Chart(plans, h: s) [
    bar(x: "plan", y: "perGb", tone: primary, labels: true)
  ],
  Table(plans, cols: [
    col("Plan", "plan"),
    col("Storage (GB)", "gb"),
    col("Price (₹/mo)", "price"),
    col("₹ / GB / mo", "perGb")
  ], sort: true),
  Callout("The 2 TB plan costs ₹0.33/GB/mo—exactly 4× cheaper per GB than the 100 GB plan.", kind: tip)
]
plans = [
  {plan: "100 GB", gb: 100, price: 130, perGb: round(130 / 100, 2)},
  {plan: "200 GB", gb: 200, price: 210, perGb: round(210 / 200, 2)},
  {plan: "2 TB", gb: 2000, price: 650, perGb: round(650 / 2000, 2)}
]
```
