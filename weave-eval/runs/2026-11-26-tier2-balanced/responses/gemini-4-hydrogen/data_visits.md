Here is your website traffic trend from May to October, showing +116.7% growth overall.

```weave
@weave 1 id: website_visits title: "Website visits"
root = Card(gap: m) [
  Title("Website visits (May–Oct)", subtitle: "6-month traffic trend", icon: "analytics"),
  Stat [
    Metric("Oct visits", lastVal, format: int, delta: pctGrowth / 100, deltaFormat: percent, deltaGood: up, tone: primary),
    Metric("Net growth", netGrowth, format: signed, tone: success),
    Metric("Total visits", totalVisits, format: int, tone: secondary)
  ],
  Chart(visits, h: m) [
    area(x: "month", y: "visits", tone: primary, curve: smooth),
    line(x: "month", y: "visits", tone: primary, curve: smooth, points: true)
  ],
  Text("Visits grew from **{firstVal | int}** in May to **{lastVal | int}** in October (**+{pctGrowth | dec(1)}%**).", style: bodySmall, tone: muted)
]
visits = [{month: "May", visits: 1200}, {month: "Jun", visits: 1500}, {month: "Jul", visits: 1350}, {month: "Aug", visits: 1800}, {month: "Sep", visits: 2100}, {month: "Oct", visits: 2600}]
firstVal = first(visits).visits
lastVal = last(visits).visits
netGrowth = lastVal - firstVal
pctGrowth = netGrowth * 100 / firstVal
totalVisits = sum(map(visits, v => v.visits))
```
