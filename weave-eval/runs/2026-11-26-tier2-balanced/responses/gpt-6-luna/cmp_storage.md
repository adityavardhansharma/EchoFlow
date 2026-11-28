Compare monthly price per GB (using 1 TB = 1,000 GB).

```weave
@weave 1 id: storage_compare title: "Cloud storage costs"
root = Column(gap: m) [
  Title("Price per GB per month"),
  Table(rows: [
    {Plan: "100 GB", "Monthly price": 130, "₹/GB/month": 130 / 100},
    {Plan: "200 GB", "Monthly price": 210, "₹/GB/month": 210 / 200},
    {Plan: "2 TB (2,000 GB)", "Monthly price": 650, "₹/GB/month": 650 / 2000}
  ], sort: true)
]
```
