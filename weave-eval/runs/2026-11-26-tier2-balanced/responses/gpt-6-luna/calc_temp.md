Move the slider to convert Celsius to Fahrenheit.
```weave
@weave 1 id: temperature title: "Temperature converter"
$c = 20
root = Card(gap: m) [Title("Temperature"), Slider($c, min: -40, max: 100, label: "Celsius", suffix: "°C"), Metric("Fahrenheit", $c * 9 / 5 + 32, format: dec(1), suffix: "°F")]
```
