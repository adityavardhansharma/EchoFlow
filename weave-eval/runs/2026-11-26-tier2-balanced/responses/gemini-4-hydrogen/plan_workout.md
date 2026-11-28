Here is a balanced weekly workout plan with 3 strength days, 2 cardio days, and 2 rest days.

```weave
@weave 1 id: weekly_workout title: "Weekly workout plan"
$type = "All"
root = Card(gap: m) [
  Title("Weekly workout split", subtitle: "3 strength · 2 cardio · 2 rest days", icon: "fitness_center"),
  Stat [
    Metric("Strength", 3, format: int, suffix: " days", tone: primary),
    Metric("Cardio", 2, format: int, suffix: " days", tone: tertiary),
    Metric("Rest", 2, format: int, suffix: " days", tone: success)
  ],
  Segmented($type, options: ["All", "Strength", "Cardio", "Rest"]),
  List(shown, render: d => Item(d.focus, subtitle: d.session, overline: "{d.day} · {d.type} · {d.duration}")),
  Button("Adapt for home equipment", icon: "home", variant: tonal, onTap: !say("Adapt this 3-strength / 2-cardio / 2-rest weekly workout plan for home dumbbells only"))
]
days = [
  {day: "Monday", type: "Strength", duration: "45 min", focus: "Upper body push & pull", session: "Bench press or push-ups, rows, overhead press, lat pulldowns, planks"},
  {day: "Tuesday", type: "Cardio", duration: "35 min", focus: "Zone 2 steady endurance", session: "Brisk jog, cycling, or rowing at conversational pace"},
  {day: "Wednesday", type: "Rest", duration: "20 min", focus: "Active recovery", session: "Easy walk, hip & shoulder mobility, foam rolling"},
  {day: "Thursday", type: "Strength", duration: "45 min", focus: "Lower body & core", session: "Squats, Romanian deadlifts, walking lunges, calf raises, dead bugs"},
  {day: "Friday", type: "Cardio", duration: "30 min", focus: "Intervals & conditioning", session: "5 min warm-up, 8 rounds of 1 min hard / 1.5 min easy, 5 min cool-down"},
  {day: "Saturday", type: "Strength", duration: "45 min", focus: "Full body compound", session: "Goblet squats, incline press, dumbbell rows, farmer carries, side planks"},
  {day: "Sunday", type: "Rest", duration: "Full day", focus: "Complete rest & sleep", session: "Light stretching, hydration, and recovery for the week ahead"}
]
shown = $type == "All" ? days : filter(days, d => d.type == $type)
on change($type) -> !haptic(tick)
```
