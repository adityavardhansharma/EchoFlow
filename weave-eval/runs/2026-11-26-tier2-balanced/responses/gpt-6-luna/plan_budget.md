Here’s a 50/30/20 split of your salary. Adjust the salary to update each amount.
```weave
@weave 1 id: budget title: "Monthly budget"
$salary = 60000
root = Card(gap: m) [Title("50 / 30 / 20 budget"), Slider($salary, min: 10000, max: 300000, label: "Monthly salary", format: inr), Stat [Metric("Needs · 50%", $salary * 50%, format: inr), Metric("Wants · 30%", $salary * 30%, format: inr), Metric("Savings · 20%", $salary * 20%, format: inr)]]
```
