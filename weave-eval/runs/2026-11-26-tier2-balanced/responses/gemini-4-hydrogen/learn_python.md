Tap a flashcard to flip between each Python list method and how it works.

```weave
@weave 1 id: learn_python title: "Python List Methods"
$idx = 0
$flipped = false
root = Card(gap: m) [
  Title("Python list methods", subtitle: "Card {$idx + 1} of 6", icon: "code"),
  Card(style: tonal, pad: l, gap: s, onTap: [!toggle($flipped), !haptic(tick)]) [
    Text($flipped ? "HOW IT WORKS" : "METHOD", style: labelSmall, tone: primary),
    Text(cur.method, style: headlineSmall),
    Column(gap: s, show: $flipped) [
      Text(cur.desc, style: bodyLarge),
      Text("Example: `{cur.ex}`", style: bodyMedium),
      Text("Returns: {cur.ret}", style: bodySmall, tone: muted)
    ],
    Text("Tap card or Flip to reveal", style: bodySmall, tone: muted, show: not $flipped)
  ],
  Row(gap: s, justify: between) [
    Button("Prev", icon: "arrow_back", variant: outlined, disabled: $idx == 0, onTap: [!dec($idx), !set($flipped, false), !haptic(tick)]),
    Button($flipped ? "Hide" : "Flip", icon: "flip", variant: tonal, onTap: [!toggle($flipped), !haptic(click)]),
    Button("Next", trailIcon: "arrow_forward", variant: filled, disabled: $idx == 5, onTap: [!inc($idx), !set($flipped, false), !haptic(tick)])
  ]
]
cards = [
  {id: 0, method: "list.append(x)", desc: "Adds a single item x to the end of the list in place.", ex: "nums.append(4)  # [1, 2, 3, 4]", ret: "None"},
  {id: 1, method: "list.extend(iter)", desc: "Appends every item from another iterable to the end.", ex: "nums.extend([4, 5])  # [1, 2, 3, 4, 5]", ret: "None"},
  {id: 2, method: "list.insert(i, x)", desc: "Inserts x at index i and shifts later elements right.", ex: "nums.insert(0, 99)  # [99, 1, 2, 3]", ret: "None"},
  {id: 3, method: "list.pop(i)", desc: "Removes and returns the item at index i (last item if omitted).", ex: "last = nums.pop()", ret: "Removed item"},
  {id: 4, method: "list.remove(x)", desc: "Removes the first occurrence of value x (errors if not found).", ex: "nums.remove(2)", ret: "None"},
  {id: 5, method: "list.sort()", desc: "Sorts the list in place; pass reverse=True for descending.", ex: "nums.sort(reverse=True)", ret: "None"}
]
cur = find(cards, c => c.id == $idx)
```
