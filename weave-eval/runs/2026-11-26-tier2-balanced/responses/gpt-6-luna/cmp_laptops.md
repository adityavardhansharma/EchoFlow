Here are the three laptops in a sortable table.

```weave
@weave 1 id: laptop_compare title: "Laptop comparison"
root = Column(gap: m) [
  Title("Laptop comparison", subtitle: "Sort by price or weight"),
  Table(rows: [
    {Model: "Air 13", Weight: 1.24, Price: 99900},
    {Model: "Zen 14", Weight: 1.39, Price: 84990},
    {Model: "Book 15", Weight: 1.6, Price: 69999}
  ], sort: true)
]
```
