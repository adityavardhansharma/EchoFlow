Flip through six common Python list methods.

```weave
@weave 1 id: python_list_flashcards title: "Python list methods"
$card = 0
root = Card(gap: m) [
  Title("List method flashcards", subtitle: "Card {$card + 1} of 6"),
  Text(["append(x) — add x to the end.", "extend(iterable) — add each item from an iterable.", "insert(i, x) — insert x at index i.", "remove(x) — remove the first matching value.", "pop(i) — remove and return the item at index i (last item by default).", "sort() — sort the list in place."][$card]),
  Button("Next card", onTap: !set($card, ($card + 1) % 6), icon: "arrow_forward")
]
```
