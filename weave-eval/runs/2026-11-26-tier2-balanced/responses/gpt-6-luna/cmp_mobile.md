Compare the effective daily price of each plan.

```weave
@weave 1 id: mobile_plan_cost title: "Mobile plan costs"
root = Column(gap: m) [
  Title("Cost per day"),
  Table(rows: [
    {Plan: "A", Price: 299, Days: 28, "Per day": 299 / 28},
    {Plan: "B", Price: 719, Days: 84, "Per day": 719 / 84},
    {Plan: "C", Price: 2999, Days: 365, "Per day": 2999 / 365}
  ], sort: true),
  Text("Plan C has the lowest daily cost (about ₹8.22/day).")
]
```
