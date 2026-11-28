Here is an interactive BMI calculator where you can adjust your weight and height.

```weave
@weave 1 id: bmi_calc title: "BMI calculator"
$weight = 70
$height = 170
root = Card(gap: m) [
  Title("BMI calculator", subtitle: "{$weight} kg · {$height} cm", icon: "monitor_weight"),
  Slider($weight, min: 30, max: 160, step: 0.5, label: "Weight", suffix: " kg"),
  Slider($height, min: 120, max: 220, step: 1, label: "Height", suffix: " cm"),
  Stat [
    Metric("Your BMI", bmiVal, format: dec(1), tone: bmiTone),
    Metric("Healthy min", minHealthy, format: dec(1), suffix: " kg", tone: muted),
    Metric("Healthy max", maxHealthy, format: dec(1), suffix: " kg", tone: muted)
  ],
  Callout("Category: **{category}**. Healthy BMI is 18.5 to 24.9 ({minHealthy | dec(1)}–{maxHealthy | dec(1)} kg for {$height} cm).", kind: info),
  Button("Ask for health tips", icon: "chat", variant: tonal, onTap: !say("My weight is {$weight} kg, height is {$height} cm, and BMI is {bmiVal | dec(1)} ({category}). What tips do you suggest?"))
]
bmiVal = $weight / pow($height / 100, 2)
minHealthy = 18.5 * pow($height / 100, 2)
maxHealthy = 24.9 * pow($height / 100, 2)
category = bmiVal < 18.5 ? "Underweight" : (bmiVal < 25 ? "Normal weight" : (bmiVal < 30 ? "Overweight" : "Obese"))
bmiTone = bmiVal < 18.5 ? info : (bmiVal < 25 ? success : (bmiVal < 30 ? warning : error))
on change($weight) -> !haptic(tick)
on change($height) -> !haptic(tick)
```
