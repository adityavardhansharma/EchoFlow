Weekly total: **33 km**; average: **4.7 km/day**.
```weave
@weave 1 id: running title: "Weekly running"
$data = [{day: "Mon", km: 5}, {day: "Tue", km: 0}, {day: "Wed", km: 7}, {day: "Thu", km: 3}, {day: "Fri", km: 0}, {day: "Sat", km: 12}, {day: "Sun", km: 6}]
root = Card(gap: m) [Title("Running log"), BarChart($data, x: "day", y: "km"), Stat [Metric("Total", 33, suffix: " km"), Metric("Daily average", 33 / 7, format: dec(1), suffix: " km")]]
```
