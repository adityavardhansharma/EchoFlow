Move the slider to convert kilometres to miles.
```weave
@weave 1 id: distance title: "Kilometres to miles"
$km = 10
root = Card(gap: m) [Title("Distance converter"), Slider($km, min: 0, max: 1000, label: "Kilometres", suffix: " km"), Metric("Miles", convert($km, "km", "mi"), format: dec(2), suffix: " mi")]
```
