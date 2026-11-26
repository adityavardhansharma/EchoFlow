Here is Delhi's monthly maximum temperature chart, with May peaking as the hottest month at 40 °C.

```weave
@weave 1 id: delhi_temp title: "Delhi monthly temperatures"
root = Card(gap: m) [
  Title("Delhi average max temperature", subtitle: "Monthly highs in °C", icon: "wb_sunny"),
  Stat [
    Metric("Hottest month", hottest.temp, prefix: "{hottest.month} · ", suffix: " °C", tone: error),
    Metric("Coolest month", coolest.temp, prefix: "{coolest.month} · ", suffix: " °C", tone: info),
    Metric("Annual avg", yearlyAvg, format: dec(1), suffix: " °C", tone: muted)
  ],
  Chart(temps, h: m) [
    line(x: "month", y: "temp", tone: error, curve: smooth, points: true),
    rule(y: yearlyAvg, tone: muted, label: "Avg {yearlyAvg | dec(1)} °C")
  ],
  Callout("**{hottest.month}** is the hottest month at **{hottest.temp} °C**, followed closely by **Jun** at **39 °C**.", kind: warning)
]
temps = [{month: "Jan", temp: 21}, {month: "Feb", temp: 24}, {month: "Mar", temp: 30}, {month: "Apr", temp: 36}, {month: "May", temp: 40}, {month: "Jun", temp: 39}, {month: "Jul", temp: 35}, {month: "Aug", temp: 34}, {month: "Sep", temp: 34}, {month: "Oct", temp: 33}, {month: "Nov", temp: 28}, {month: "Dec", temp: 23}]
hottest = first(sortBy(temps, "temp", desc: true))
coolest = first(sortBy(temps, "temp", desc: false))
yearlyAvg = avg(map(temps, t => t.temp))
```
