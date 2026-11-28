Change the price to update your savings and final price.
```weave
@weave 1 id: discount title: "Discount calculator"
$price = 2499
root = Card(gap: m) [Title("30% off"), Slider($price, min: 100, max: 20000, label: "Price", format: inr), Metric("You save", $price * 30%, format: inr), Metric("Final price", $price * 70%, format: inr)]
```
