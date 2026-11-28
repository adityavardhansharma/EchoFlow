Enter your weight and height to calculate your BMI.

```weave
@weave 1 id: bmi title: "BMI calculator"
$weight = 70
$height = 170
root = Card(gap: m) [
  Title("BMI calculator"),
  Slider($weight, min: 30, max: 200, label: "Weight", suffix: " kg"),
  Slider($height, min: 100, max: 220, label: "Height", suffix: " cm"),
  Metric("BMI", bmi($weight, $height / 100), format: dec(1))
]
```
