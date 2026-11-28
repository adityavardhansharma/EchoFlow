Visits rose by **1,400 (116.7%)** from May to October.
```weave
@weave 1 id: visits title: "Website visits"
$data = [{month: "May", visits: 1200}, {month: "Jun", visits: 1500}, {month: "Jul", visits: 1350}, {month: "Aug", visits: 1800}, {month: "Sep", visits: 2100}, {month: "Oct", visits: 2600}]
root = Card(gap: m) [Title("Monthly visits"), LineChart($data, x: "month", y: "visits"), Metric("Growth", 1400, format: percent, delta: 116.7)]
```
