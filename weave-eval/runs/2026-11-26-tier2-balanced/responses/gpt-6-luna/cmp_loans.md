Compare the estimated monthly payments and total interest.

```weave
@weave 1 id: home_loan_compare title: "Home loan offers"
fn interest(rate, years) = emi(4000000, rate, years) * years * 12 - 4000000
root = Column(gap: m) [
  Title("₹40 lakh loan comparison"),
  Card(gap: s) [
    Title("Bank A · 8.5% · 20 years"),
    Metric("Monthly EMI", emi(4000000, 0.085, 20), format: inr, tone: primary),
    Metric("Total interest", interest(0.085, 20), format: inr, tone: tertiary)
  ],
  Card(gap: s) [
    Title("Bank B · 8.9% · 15 years"),
    Metric("Monthly EMI", emi(4000000, 0.089, 15), format: inr, tone: primary),
    Metric("Total interest", interest(0.089, 15), format: inr, tone: tertiary)
  ],
  Text("Estimates assume fixed rates and monthly repayments; fees and rate changes are excluded.")
]
```
