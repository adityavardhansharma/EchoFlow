Here is a structured 7:00 AM to 10:00 PM remote-work routine balancing deep focus blocks, restorative breaks, and evening wind-down.

```weave
@weave 1 id: remote_routine title: "Remote worker daily routine"
$filter = "All"
root = Card(gap: m) [
  Title("Remote worker routine", subtitle: "7:00 AM – 10:00 PM · Focus & breaks", icon: "schedule"),
  Stat [
    Metric("Focus work", focusHrs, format: dec(1), suffix: " hrs", tone: primary),
    Metric("Breaks & meals", breakHrs, format: dec(1), suffix: " hrs", tone: success)
  ],
  Segmented($filter, options: ["All", "Focus", "Break", "Life"]),
  List(shown, render: b => Item(b.title, subtitle: b.detail, overline: "{b.time} · {b.type}"))
]
blocks = [
  {time: "7:00 – 8:00 AM", title: "Wake up & morning movement", detail: "Hydrate, 25-min walk or workout, shower", type: "Life", mins: 60},
  {time: "8:00 – 9:00 AM", title: "Breakfast & daily plan", detail: "Screen-free meal; pick top 3 work priorities", type: "Break", mins: 60},
  {time: "9:00 – 11:00 AM", title: "Deep Focus Block 1", detail: "Highest-priority creative or coding work, notifications off", type: "Focus", mins: 120},
  {time: "11:00 – 11:20 AM", title: "Morning recharge break", detail: "Step away from screen, stretch, tea or coffee", type: "Break", mins: 20},
  {time: "11:20 AM – 1:00 PM", title: "Focus Block 2 & syncs", detail: "Meetings, reviews, collaborative work", type: "Focus", mins: 100},
  {time: "1:00 – 2:00 PM", title: "Lunch & outdoor walk", detail: "Full hour away from the desk", type: "Break", mins: 60},
  {time: "2:00 – 4:00 PM", title: "Deep Focus Block 3", detail: "Second deep-work sprint on core deliverables", type: "Focus", mins: 120},
  {time: "4:00 – 4:20 PM", title: "Afternoon reset", detail: "Healthy snack, stretch, rest eyes", type: "Break", mins: 20},
  {time: "4:20 – 5:30 PM", title: "Admin & daily wrap-up", detail: "Inbox zero, Slack replies, prep tomorrow's list", type: "Focus", mins: 70},
  {time: "5:30 – 7:30 PM", title: "Shutdown & personal time", detail: "Close laptop; hobbies, errands, or social time", type: "Life", mins: 120},
  {time: "7:30 – 8:30 PM", title: "Dinner", detail: "Unplugged evening meal", type: "Break", mins: 60},
  {time: "8:30 – 10:00 PM", title: "Wind-down & sleep prep", detail: "Reading, dim lights, screens off by 9:45 PM", type: "Life", mins: 90}
]
focusHrs = sum(map(filter(blocks, b => b.type == "Focus"), b => b.mins)) / 60
breakHrs = sum(map(filter(blocks, b => b.type == "Break"), b => b.mins)) / 60
shown = $filter == "All" ? blocks : filter(blocks, b => b.type == $filter)
on change($filter) -> !haptic(tick)
```
