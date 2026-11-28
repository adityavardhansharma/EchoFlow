Pick a multiplication table and practise the facts from 1 to 12.

```weave
@weave 1 id: multiplication_practice title: "Multiplication practice"
$table = 7
root = Column(gap: m) [
  Title("Multiplication tables"),
  Slider($table, min: 1, max: 12, step: 1, label: "Choose a table"),
  List(items: map(range(1, 13), n => "{$table} × {n} = {$table * n}"))
]
```
