Here is the 10-year comparison between ₹30,000/month rent (rising 5% a year) and a fixed ₹55,000/month home loan EMI.

```weave
@weave 1 id: cmp_rent title: "Rent vs Buy (10 Years)"
$hike = 5
root = Card(gap: m) [
  Title("Rent vs buy over 10 years", subtitle: "Rent starts at ₹30,000/mo vs fixed ₹55,000/mo EMI", icon: "home"),
  Slider($hike, min: 0, max: 12, step: 1, label: "Annual rent increase", suffix: "%"),
  Stat [
    Metric("Total rent (10 yrs)", totalRent, format: inr, tone: secondary),
    Metric("Total EMI (10 yrs)", totalEmi, format: inr, tone: primary),
    Metric("Cash difference", totalEmi - totalRent, format: inr, tone: success)
  ],
  Chart(series, h: m) [
    line(x: "year", y: "emi", tone: primary, points: true),
    line(x: "year", y: "rent", tone: tertiary, points: true)
  ],
  Callout("By Year 10, monthly rent reaches {round(30000 * pow(1 + $hike / 100, 9), 0) | inr}/mo, though total rent paid stays {totalEmi - totalRent | inr} below total EMIs.", kind: info)
]
years = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
fn cumRent(y) = $hike == 0 ? 360000 * y : round(360000 * (pow(1 + $hike / 100, y) - 1) / ($hike / 100), 0)
totalRent = cumRent(10)
totalEmi = 55000 * 120
series = map(years, y => {year: y, rent: cumRent(y), emi: 55000 * 12 * y})
on change($hike) -> !haptic(tick)
```
