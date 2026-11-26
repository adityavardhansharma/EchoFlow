Change the distance to update the trip estimate.
```weave
@weave 1 id: fuel title: "Trip fuel cost"
$distance = 350
root = Card(gap: m) [Title("Fuel estimate"), Slider($distance, min: 10, max: 2000, label: "Distance", suffix: " km"), Metric("Estimated cost", $distance / 15 * 105, format: inr)]
```
