```weave
@weave 1 id: expenses title: "Monthly expenses"
$data = [{category: "Rent", amount: 18000}, {category: "Food", amount: 9000}, {category: "Travel", amount: 4000}, {category: "Bills", amount: 3500}, {category: "Fun", amount: 5500}]
root = Card(gap: m) [Title("Monthly expenses"), BarChart($data, x: "category", y: "amount"), Table($data), Metric("Total", sum(map($data, x => x.amount)), format: inr)]
```
