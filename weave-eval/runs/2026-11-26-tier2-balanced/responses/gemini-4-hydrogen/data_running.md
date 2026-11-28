Here is your weekly running log chart with the total distance and daily average.

```weave
@weave 1 id: running_log title: "Weekly running log"
root = Card(gap: m) [
  Title("Weekly running log", subtitle: "{activeDays} active days · 2 rest days", icon: "directions_run"),
  Stat [
    Metric("Weekly total", totalKm, format: dec(1), suffix: " km", tone: primary),
    Metric("Daily avg", avgDay, format: dec(1), suffix: " km", tone: secondary),
    Metric("Longest run", longest, format: dec(1), suffix: " km", tone: success)
  ],
  Chart(runs, h: m) [
    bar(x: "day", y: "km", tone: primary),
    rule(y: avgDay, tone: tertiary, label: "Avg {avgDay | dec(1)} km")
  ]
]
runs = [{day: "Mon", km: 5}, {day: "Tue", km: 0}, {day: "Wed", km: 7}, {day: "Thu", km: 3}, {day: "Fri", km: 0}, {day: "Sat", km: 12}, {day: "Sun", km: 6}]
totalKm = sum(map(runs, r => r.km))
avgDay = avg(map(runs, r => r.km))
activeDays = len(filter(runs, r => r.km > 0))
longest = maxOf(map(runs, r => r.km))
```
