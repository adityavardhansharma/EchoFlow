Tap each step as you finish it.
```weave
@weave 1 id: morning title: "Morning routine"
$done = 0
root = Card(gap: m) [Title("Morning checklist"), Text("Completed: {$done} of 6"), Button("Wake up and make the bed", onTap: !inc($done)), Button("Drink a glass of water", onTap: !inc($done)), Button("Stretch for five minutes", onTap: !inc($done)), Button("Wash up and get dressed", onTap: !inc($done)), Button("Eat a nourishing breakfast", onTap: !inc($done)), Button("Review today’s priorities", onTap: !inc($done))]
```
