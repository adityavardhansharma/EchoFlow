Adjust the base and height of the right triangle to see how $a^2 + b^2 = c^2$ gives the hypotenuse.

```weave
@weave 1 id: learn_pythagoras title: "Pythagoras' Theorem"
$base = 3
$height = 4
root = Card(gap: m) [
  Title("Pythagoras' theorem", subtitle: "a² + b² = c² in a right-angled triangle", icon: "change_history"),
  Slider($base, min: 1, max: 20, step: 1, label: "Base (a)"),
  Slider($height, min: 1, max: 20, step: 1, label: "Height (b)"),
  Row(gap: xs, wrap: auto) [
    Button("3-4-5", size: xs, variant: tonal, onTap: [!set($base, 3), !set($height, 4), !haptic(click)]),
    Button("5-12-13", size: xs, variant: tonal, onTap: [!set($base, 5), !set($height, 12), !haptic(click)]),
    Button("8-15-17", size: xs, variant: tonal, onTap: [!set($base, 8), !set($height, 15), !haptic(click)])
  ],
  Stat [
    Metric("Hypotenuse (c)", hyp, format: num(2), tone: primary),
    Metric("c² = a² + b²", c2, format: int, tone: secondary),
    Metric("Area (½ab)", 0.5 * $base * $height, format: num(1), tone: tertiary)
  ],
  Card(style: tonal, pad: m, gap: xs) [
    Text("Step-by-step", style: labelMedium, tone: muted),
    Text("{$base}² + {$height}² = {a2} + {b2} = {c2}", style: titleSmall),
    Text("c = √{c2} = {hyp | num(2)}", style: titleMedium, tone: primary)
  ],
  Chart(squares, h: s) [
    bar(x: "side", y: "area", tone: primary, labels: true)
  ]
]
a2 = $base * $base
b2 = $height * $height
c2 = a2 + b2
hyp = sqrt(c2)
squares = [
  {side: "a² (base²)", area: a2},
  {side: "b² (height²)", area: b2},
  {side: "c² (hyp²)", area: c2}
]
on change($base) -> !haptic(tick)
on change($height) -> !haptic(tick)
```
