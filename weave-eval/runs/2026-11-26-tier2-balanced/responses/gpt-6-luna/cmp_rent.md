Explore the 10-year totals. Rent rises by 5% each year, while the EMI stays fixed.

```weave
@weave 1 id: rent_buy title: "Rent vs home loan"
fn rentTotal(year) = 30000 * 12 * pow(1.05, year)
root = Card(gap: m) [
  Title("Total paid over 10 years"),
  Metric("Rent", sum(map(range(0, 10), y => rentTotal(y))), format: inr, tone: tertiary),
  Metric("Home loan EMI", 55000 * 12 * 10, format: inr, tone: primary),
  Text("Rent assumes a 5% increase at each year boundary. EMI total excludes down payment, fees, maintenance and any home value.")
]
```
