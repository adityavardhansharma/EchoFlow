Here is an interactive 6-step morning routine checklist that tracks how many steps you have completed.

```weave
@weave 1 id: morning_routine title: "Morning routine"
$steps = [
  {id: 0, label: "Drink a full glass of water", mins: 2, done: false},
  {id: 1, label: "10-minute stretch or brisk walk", mins: 10, done: false},
  {id: 2, label: "Shower and get dressed", mins: 15, done: false},
  {id: 3, label: "Eat a protein-rich breakfast", mins: 15, done: false},
  {id: 4, label: "5 minutes mindfulness or journaling", mins: 5, done: false},
  {id: 5, label: "Pick your top 3 priorities for today", mins: 5, done: false}
]
root = Card(gap: m) [
  Title("Morning routine", subtitle: "{doneCount} of {totalCount} steps done", icon: "wb_twilight"),
  Stat [
    Metric("Completed", doneCount, format: int, suffix: "/{totalCount}", tone: success),
    Metric("Remaining", totalCount - doneCount, format: int, tone: muted)
  ],
  List($steps, render: s => Row(justify: between, align: center) [
    Column(gap: xxs, w: 3/4) [
      Text(s.label, style: bodyLarge, strike: s.done, tone: s.done ? muted : neutral),
      Text("{s.mins} min", style: bodySmall, tone: muted)
    ],
    Button(s.done ? "Done" : "Mark", icon: s.done ? "check_circle" : "radio_button_unchecked", variant: s.done ? tonal : outlined, tone: s.done ? success : primary, size: xs, onTap: [!set($steps, map($steps, x => x.id == s.id ? merge(x, {done: not x.done}) : x)), !haptic(click)])
  ]),
  Button("Reset all", icon: "refresh", variant: text, onTap: [!reset($steps), !haptic(tick)])
]
doneCount = len(filter($steps, s => s.done))
totalCount = len($steps)
on change($steps) -> !if(doneCount == totalCount, !confetti)
```
