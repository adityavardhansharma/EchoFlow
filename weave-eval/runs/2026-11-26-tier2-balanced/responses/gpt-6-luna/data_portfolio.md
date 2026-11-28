```weave
@weave 1 id: portfolio title: "Portfolio allocation"
$data = [{asset: "Equity", amount: 600000}, {asset: "Debt", amount: 250000}, {asset: "Gold", amount: 100000}, {asset: "Cash", amount: 50000}]
root = Card(gap: m) [Title("₹10 lakh portfolio"), BarChart($data, x: "asset", y: "amount"), Table($data)]
```
