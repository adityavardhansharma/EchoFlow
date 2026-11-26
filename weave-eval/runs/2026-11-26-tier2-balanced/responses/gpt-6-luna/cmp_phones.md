Compare the listed prices and battery capacities.

```weave
@weave 1 id: phone_compare title: "Phone comparison"
root = Column(gap: m) [
  Title("Phones"),
  Table(rows: [
    {Model: "Pixel 9", Price: 79999, "Battery (mAh)": 4700},
    {Model: "Galaxy S25", Price: 80999, "Battery (mAh)": 3900},
    {Model: "iPhone 16", Price: 79900, "Battery (mAh)": 3561}
  ], sort: true),
  Chart([
    {Model: "Pixel 9", Battery: 4700},
    {Model: "Galaxy S25", Battery: 3900},
    {Model: "iPhone 16", Battery: 3561}
  ], x: "Model", y: "Battery", h: m) [
    bar(x: "Model", y: "Battery", labels: true)
  ]
]
```
