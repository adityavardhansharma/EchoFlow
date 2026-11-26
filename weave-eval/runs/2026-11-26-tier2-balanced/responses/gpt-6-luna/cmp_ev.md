Adjust monthly distance to compare energy and fuel costs.

```weave
@weave 1 id: ev_running_cost title: "EV vs petrol"
$km = 1000
root = Card(gap: m) [
  Title("Monthly running cost", subtitle: "Energy and fuel only"),
  Slider($km, min: 0, max: 5000, step: 100, label: "Distance", suffix: " km"),
  Stat [
    Metric("EV · ₹1.20/km", $km * 15 / 100 * 8, format: inr, tone: primary),
    Metric("Petrol · ₹7/km", $km / 15 * 105, format: inr, tone: tertiary)
  ],
  Text("Based on 15 kWh/100 km at ₹8/kWh and 15 km/litre at ₹105/litre.")
]
```
