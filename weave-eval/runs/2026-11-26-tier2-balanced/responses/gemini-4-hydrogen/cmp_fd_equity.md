Slide the investment horizon to see how ₹1,00,000 grows at 7% in an FD versus 12% in equity.

```weave
@weave 1 id: cmp_fd_equity title: "FD (7%) vs Equity (12%)"
$years = 10
root = Card(gap: m) [
  Title("₹1,00,000 growth over {$years} yrs", subtitle: "Annual compounding: 7% FD vs 12% Equity", icon: "trending_up"),
  Slider($years, min: 1, max: 25, step: 1, label: "Years", suffix: " yrs"),
  Stat [
    Metric("FD (7%)", fdEnd, format: inr, tone: secondary),
    Metric("Equity (12%)", eqEnd, format: inr, tone: primary),
    Metric("Extra wealth", eqEnd - fdEnd, format: inr, tone: success)
  ],
  Chart(series, h: m) [
    line(x: "year", y: "equity", tone: primary, points: true),
    line(x: "year", y: "fd", tone: tertiary, points: true)
  ],
  Callout("After {$years} years, equity grows to {round(eqEnd / 100000, 2)}× your money vs {round(fdEnd / 100000, 2)}× in the FD.", kind: info)
]
allYears = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25]
fdEnd = round(100000 * pow(1.07, $years), 0)
eqEnd = round(100000 * pow(1.12, $years), 0)
series = map(take(allYears, $years), y => {year: y, fd: round(100000 * pow(1.07, y), 0), equity: round(100000 * pow(1.12, y), 0)})
on change($years) -> !haptic(tick)
```
