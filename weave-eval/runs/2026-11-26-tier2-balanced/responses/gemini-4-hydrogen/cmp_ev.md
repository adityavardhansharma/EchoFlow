Adjust your monthly driving distance to compare EV and petrol running costs.

```weave
@weave 1 id: cmp_ev title: "EV vs Petrol Running Cost"
$km = 1000
root = Card(gap: m) [
  Title("EV vs petrol running cost", subtitle: "{$km} km driven per month", icon: "ev_station"),
  Slider($km, min: 100, max: 3000, step: 50, label: "Monthly distance", suffix: " km"),
  Stat [
    Metric("EV (₹1.20/km)", evCost, format: inr, tone: success),
    Metric("Petrol (₹7.00/km)", petrolCost, format: inr, tone: error),
    Metric("Monthly savings", petrolCost - evCost, format: inr, tone: primary)
  ],
  Chart(rows, h: s) [
    bar(x: "type", y: "cost", tone: primary, labels: true)
  ],
  Callout("At {$km} km/month, the EV uses {round($km * 0.15, 1)} kWh while petrol uses {round($km / 15, 1)} L—saving {(petrolCost - evCost) * 12 | inr} a year.", kind: tip)
]
evCost = round($km * 0.15 * 8, 0)
petrolCost = round($km / 15 * 105, 0)
rows = [
  {type: "EV", cost: evCost},
  {type: "Petrol", cost: petrolCost}
]
on change($km) -> !haptic(tick)
```
