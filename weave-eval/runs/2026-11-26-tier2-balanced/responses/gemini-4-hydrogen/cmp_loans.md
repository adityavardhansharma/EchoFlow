Here is the EMI and total interest comparison for the two ₹40 lakh home loan offers.

```weave
@weave 1 id: cmp_loans title: "₹40L Home Loan Comparison"
$view = "summary"
root = Card(gap: m) [
  Title("₹40 lakh home loan offers", subtitle: "Bank A (8.5%, 20 yrs) vs Bank B (8.9%, 15 yrs)", icon: "account_balance"),
  Segmented($view, options: ["summary", "schedule"], labels: ["Compare", "Payoff curve"]),
  Column(gap: s, show: $view == "summary") [
    Card(style: tonal, pad: m, gap: xs) [
      Title("Bank A — 8.5% for 20 years", style: titleSmall),
      Stat [
        Metric("Monthly EMI", emiA, format: inr, tone: primary),
        Metric("Total interest", intA, format: inrCompact, tone: tertiary)
      ]
    ],
    Card(style: tonal, pad: m, gap: xs) [
      Title("Bank B — 8.9% for 15 years", style: titleSmall),
      Stat [
        Metric("Monthly EMI", emiB, format: inr, tone: secondary),
        Metric("Total interest", intB, format: inrCompact, tone: success)
      ]
    ],
    Chart(bars, h: s) [
      bar(x: "offer", y: "principal", tone: primary, stack: true),
      bar(x: "offer", y: "interest", tone: tertiary, stack: true)
    ]
  ],
  Column(gap: s, show: $view == "schedule") [
    Text("Remaining principal balance over time", style: labelMedium, tone: muted),
    Chart(amortize(4000000, 0.085, 20), h: s) [
      area(x: "year", y: "balance", tone: primary)
    ],
    Text("Bank B (15 yrs) clears the loan 5 years sooner", style: labelSmall, tone: muted),
    Chart(amortize(4000000, 0.089, 15), h: s) [
      area(x: "year", y: "balance", tone: secondary)
    ]
  ],
  Callout("Bank A keeps your EMI {emiB - emiA | inr}/mo lower, but Bank B saves {intA - intB | inr} in total interest despite the higher rate.", kind: tip)
]
emiA = round(emi(4000000, 0.085, 20), 0)
emiB = round(emi(4000000, 0.089, 15), 0)
intA = emiA * 240 - 4000000
intB = emiB * 180 - 4000000
bars = [
  {offer: "Bank A (20y)", principal: 4000000, interest: intA},
  {offer: "Bank B (15y)", principal: 4000000, interest: intB}
]
on change($view) -> !haptic(tick)
```
