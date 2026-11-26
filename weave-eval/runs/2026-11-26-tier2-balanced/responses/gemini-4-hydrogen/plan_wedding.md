Here is an interactive wedding planning timeline from 6 months out to the wedding day, with key tasks you can filter by milestone and check off.

```weave
@weave 1 id: wedding_timeline title: "Wedding planning timeline"
$phase = "All"
$tasks = [
  {id: 0, phase: "6 Months", task: "Set total budget & draft guest list", done: false},
  {id: 1, phase: "6 Months", task: "Book venue, caterer & wedding date", done: false},
  {id: 2, phase: "6 Months", task: "Hire photographer, videographer & planner", done: false},
  {id: 3, phase: "4 Months", task: "Send save-the-dates & book guest rooms", done: false},
  {id: 4, phase: "4 Months", task: "Finalise wedding outfits, jewellery & stylist", done: false},
  {id: 5, phase: "4 Months", task: "Book florist, decor theme & DJ / music", done: false},
  {id: 6, phase: "2 Months", task: "Send formal invitations & complete menu tasting", done: false},
  {id: 7, phase: "2 Months", task: "Arrange transport & marriage licence paperwork", done: false},
  {id: 8, phase: "1 Month", task: "Confirm RSVPs & finalise seating chart", done: false},
  {id: 9, phase: "1 Month", task: "Complete final outfit fittings & vendor run-sheet", done: false},
  {id: 10, phase: "1 Week", task: "Walkthrough rehearsal & confirm all vendor timings", done: false},
  {id: 11, phase: "1 Week", task: "Prepare rings, vendor payments & emergency kit", done: false},
  {id: 12, phase: "Wedding Day", task: "Eat breakfast, stay hydrated & delegate to coordinator", done: false},
  {id: 13, phase: "Wedding Day", task: "Exchange rings & celebrate with family and friends", done: false}
]
root = Card(gap: m) [
  Title("Wedding timeline", subtitle: "{doneCount} of {totalCount} tasks complete", icon: "favorite"),
  Select($phase, options: ["All", "6 Months", "4 Months", "2 Months", "1 Month", "1 Week", "Wedding Day"], label: "Milestone"),
  List(shown, render: t => Row(justify: between, align: center) [
    Column(gap: xxs, w: 3/4) [
      Text(t.phase, style: labelSmall, tone: primary),
      Text(t.task, style: bodyMedium, strike: t.done, tone: t.done ? muted : neutral)
    ],
    Button(t.done ? "Done" : "Tick", icon: t.done ? "check_circle" : "radio_button_unchecked", variant: t.done ? tonal : outlined, tone: t.done ? success : primary, size: xs, onTap: [!set($tasks, map($tasks, x => x.id == t.id ? merge(x, {done: not x.done}) : x)), !haptic(click)])
  ])
]
doneCount = len(filter($tasks, t => t.done))
totalCount = len($tasks)
shown = $phase == "All" ? $tasks : filter($tasks, t => t.phase == $phase)
on change($phase) -> !haptic(tick)
```
