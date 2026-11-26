Here is your discount calculator starting at ₹2,499 with 30% off.

```weave
@weave 1 id: discount_calc title: "Discount calculator"
$price = 2499
$discount = 30
root = Card(gap: m) [
  Title("Shopping discount", subtitle: "{$discount}% off {$price | inr}", icon: "local_offer"),
  Slider($price, min: 100, max: 20000, step: 1, label: "Original price", format: inr),
  Slider($discount, min: 0, max: 80, step: 5, label: "Discount", suffix: "%"),
  Stat [
    Metric("Final price", finalPrice, format: inr, tone: primary, size: l),
    Metric("You save", savings, format: inr, tone: success)
  ],
  Callout("At **{$discount}% off**, you pay **{100 - $discount}%** of the tag price.", kind: tip)
]
savings = $price * $discount / 100
finalPrice = $price - savings
on change($discount) -> !haptic(tick)
```
