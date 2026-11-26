Here is your 6-month electricity usage chart and total cost calculation at ₹7 per unit.

```weave
@weave 1 id: electricity_usage title: "Electricity usage"
$rate = 7
root = Card(gap: m) [
  Title("Electricity usage (Jan–Jun)", subtitle: "{totalUnits | int} units total at ₹{$rate}/unit", icon: "bolt"),
  Stat [
    Metric("Total units", totalUnits, format: int, suffix: " kWh", tone: secondary),
    Metric("Total cost", totalCost, format: inr, tone: primary),
    Metric("Monthly avg", avgUnits, format: dec(1), suffix: " kWh", tone: muted)
  ],
  BarChart(usage, x: "month", y: "units", h: m),
  Slider($rate, min: 4, max: 12, step: 0.5, label: "Tariff rate", prefix: "₹", suffix: "/unit")
]
usage = [{month: "Jan", units: 220}, {month: "Feb", units: 190}, {month: "Mar", units: 210}, {month: "Apr", units: 280}, {month: "May", units: 350}, {month: "Jun", units: 400}]
totalUnits = sum(map(usage, u => u.units))
totalCost = totalUnits * $rate
avgUnits = avg(map(usage, u => u.units))
on change($rate) -> !haptic(tick)
```
