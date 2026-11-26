Pick any multiplication table from 1 to 12 to review it or switch to Quiz mode to test yourself.

```weave
@weave 1 id: learn_tables title: "Multiplication Tables (1–12)"
$table = 7
$mode = "study"
$step = 0
$ans = ""
$checked = false
$score = 0
root = Card(gap: m) [
  Title("Table of {$table}", subtitle: "Multiplication practice (1 to 12)", icon: "calculate"),
  Slider($table, min: 1, max: 12, step: 1, label: "Choose table"),
  Segmented($mode, options: ["study", "quiz"], labels: ["Study table", "Quiz me"]),
  Column(gap: xs, show: $mode == "study") [
    List([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12], render: n => Row(justify: between) [
      Text("{$table} × {n}", style: bodyLarge),
      Text("{$table * n}", style: titleMedium, tone: primary)
    ])
  ],
  Column(gap: m, show: $mode == "quiz") [
    Text("Question {$step + 1} of 12 • Score: {$score}", style: labelMedium, tone: muted),
    Text("What is **{$table} × {mult}**?", style: headlineSmall),
    TextField($ans, label: "Your answer", kind: number, placeholder: "Enter product"),
    Callout(isRight ? "Correct! {$table} × {mult} = {$table * mult}." : "Not quite—{$table} × {mult} = {$table * mult}.", kind: isRight ? success : error, show: $checked),
    Row(gap: s) [
      Button("Check", icon: "check", wide: true, disabled: $checked or $ans == "", onTap: [!set($checked, true), !if(isRight, [!inc($score), !haptic(confirm)], !haptic(reject))]),
      Button($step < 11 ? "Next" : "Restart", wide: true, show: $checked, onTap: !if($step < 11, [!inc($step), !set($ans, ""), !set($checked, false), !haptic(tick)], [!set($step, 0), !set($ans, ""), !set($checked, false), !set($score, 0), !haptic(click)]))
    ]
  ]
]
order = [
  {id: 0, m: 7}, {id: 1, m: 3}, {id: 2, m: 11}, {id: 3, m: 5},
  {id: 4, m: 9}, {id: 5, m: 2}, {id: 6, m: 12}, {id: 7, m: 4},
  {id: 8, m: 8}, {id: 9, m: 6}, {id: 10, m: 10}, {id: 11, m: 1}
]
mult = find(order, o => o.id == $step).m
isRight = replace($ans, " ", "") == str($table * mult)
on change($table) -> [!set($step, 0), !set($ans, ""), !set($checked, false), !set($score, 0), !haptic(tick)]
```
