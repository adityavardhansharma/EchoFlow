Here is a savings goal planner starting at ₹3,00,000 over 12 months, with sliders to adjust your timeline and target.

```weave
@weave 1 id: savings_planner title: "Savings goal planner"
$goal = 300000
$months = 12
$saved = 0
root = Card(gap: m) [
  Title("Savings goal planner", subtitle: "{$goal | inr} in {$months} months", icon: "savings"),
  Slider($months, min: 3, max: 60, step: 1, label: "Timeline", suffix: " months"),
  Slider($goal, min: 50000, max: 1000000, step: 10000, label: "Goal amount", format: inr),
  Slider($saved, min: 0, max: 250000, step: 5000, label: "Already saved", format: inr),
  Stat [
    Metric("Save per month", monthlySave, format: inr, tone: primary),
    Metric("Per week", weeklySave, format: inr, tone: secondary),
    Metric("Remaining", remaining, format: inrCompact, tone: tertiary)
  ],
  Callout("Set aside **{monthlySave | inr}** every month to hit **{$goal | inr}** in **{$months} months**.", kind: tip)
]
remaining = max(0, $goal - $saved)
monthlySave = remaining / $months
weeklySave = monthlySave / 4.33
on change($months) -> !haptic(tick)
```
