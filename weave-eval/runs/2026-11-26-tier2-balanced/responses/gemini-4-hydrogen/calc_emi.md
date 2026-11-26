Here is an interactive EMI calculator for your ₹50 lakh, 20-year home loan starting at 8%.

```weave
@weave 1 id: emi_calc title: "Home loan EMI"
$principal = 5000000
$rate = 8
$years = 20
root = Card(gap: m) [
  Title("Home loan EMI", subtitle: "{$principal | inr} over {$years} years", icon: "home"),
  Slider($rate, min: 6, max: 14, step: 0.05, label: "Interest rate", suffix: "%"),
  Segmented($years, options: [10, 15, 20, 25, 30], suffix: " yrs"),
  Stat [
    Metric("Monthly EMI", monthly, format: inr, tone: primary),
    Metric("Total interest", totalInterest, format: inrCompact, tone: tertiary),
    Metric("Total payable", totalPayable, format: inrCompact, tone: secondary)
  ],
  Chart(amortize($principal, $rate / 100, $years), h: m) [
    area(x: "year", y: "principal", tone: primary, stack: true),
    area(x: "year", y: "interest", tone: tertiary, stack: true)
  ],
  Button("Compare bank offers", icon: "compare_arrows", variant: tonal, onTap: !say("Compare home loan rates for {$principal | inr} at {$rate}% over {$years} years"))
]
monthly = emi($principal, $rate / 100, $years)
totalPayable = monthly * $years * 12
totalInterest = totalPayable - $principal
on change($years) -> !haptic(tick)
```
