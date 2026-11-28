Use the slider or presets below to convert kilometres to miles.

```weave
@weave 1 id: km_to_mi title: "Kilometres to miles"
$km = 10
root = Card(gap: m) [
  Title("Kilometres to miles", subtitle: "1 km ≈ 0.6214 miles", icon: "straighten"),
  Slider($km, min: 0, max: 500, step: 1, label: "Kilometres", suffix: " km"),
  Segmented($km, options: [5, 10, 21.1, 42.2, 100], suffix: " km"),
  Stat [
    Metric("Kilometres", $km, format: dec(1), suffix: " km", tone: secondary),
    Metric("Miles", miles, format: num(2), suffix: " mi", tone: primary)
  ]
]
miles = convert($km, "km", "mi")
on change($km) -> !haptic(tick)
```
