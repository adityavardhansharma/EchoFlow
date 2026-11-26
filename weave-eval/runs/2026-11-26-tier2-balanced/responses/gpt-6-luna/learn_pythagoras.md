Adjust the two perpendicular sides and see how the hypotenuse changes.

```weave
@weave 1 id: pythagoras_demo title: "Pythagoras' theorem"
$base = 3
$height = 4
root = Card(gap: m) [
  Title("Right triangle", subtitle: "For legs a and b, the hypotenuse c = √(a² + b²)."),
  Slider($base, min: 1, max: 20, step: 1, label: "Base"),
  Slider($height, min: 1, max: 20, step: 1, label: "Height"),
  Metric("Hypotenuse", sqrt(pow($base, 2) + pow($height, 2)), format: dec(2), tone: primary)
]
```
