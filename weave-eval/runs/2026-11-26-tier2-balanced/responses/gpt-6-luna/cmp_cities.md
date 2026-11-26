Compare the monthly city budgets:

```weave
@weave 1 id: city_costs title: "Monthly city costs"
root = Column(gap: m) [
  Title("Monthly costs", subtitle: "Rent, food and transport"),
  Table(rows: [
    {City: "Bengaluru", Rent: 28000, Food: 12000, Transport: 4000, Total: 44000},
    {City: "Pune", Rent: 20000, Food: 10000, Transport: 3000, Total: 33000},
    {City: "Hyderabad", Rent: 18000, Food: 10000, Transport: 3500, Total: 31500}
  ], sort: true)
]
```
