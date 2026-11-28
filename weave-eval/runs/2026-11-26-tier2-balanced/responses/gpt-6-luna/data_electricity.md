At ₹7 per unit, 1,650 units cost **₹11,550**.
```weave
@weave 1 id: electricity title: "Electricity use"
$data = [{month: "Jan", units: 220}, {month: "Feb", units: 190}, {month: "Mar", units: 210}, {month: "Apr", units: 280}, {month: "May", units: 350}, {month: "Jun", units: 400}]
root = Card(gap: m) [Title("Monthly units"), BarChart($data, x: "month", y: "units"), Metric("Total cost", sum(map($data, x => x.units)) * 7, format: inr)]
```
