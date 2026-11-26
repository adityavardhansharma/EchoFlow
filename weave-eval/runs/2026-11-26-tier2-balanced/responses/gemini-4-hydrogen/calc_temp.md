Slide the Celsius temperature below to see the equivalent in Fahrenheit and Kelvin.

```weave
@weave 1 id: temp_conv title: "Celsius to Fahrenheit"
$celsius = 25
root = Card(gap: m) [
  Title("Temperature converter", subtitle: "Formula: (°C × 9/5) + 32", icon: "thermostat"),
  Slider($celsius, min: -40, max: 100, step: 0.5, label: "Celsius", suffix: " °C"),
  Segmented($celsius, options: [0, 20, 25, 37, 100], suffix: " °C"),
  Stat [
    Metric("Fahrenheit", fahrenheit, format: dec(1), suffix: " °F", tone: primary),
    Metric("Kelvin", kelvin, format: num(2), suffix: " K", tone: secondary)
  ],
  Callout(note, kind: info)
]
fahrenheit = $celsius * 9 / 5 + 32
kelvin = $celsius + 273.15
note = $celsius <= 0 ? "At or below water's freezing point." : ($celsius < 18 ? "Cool weather." : ($celsius < 28 ? "Comfortable room temperature." : ($celsius < 40 ? "Warm to hot weather." : "Extreme heat / approaching boiling.")))
on change($celsius) -> !haptic(tick)
```
