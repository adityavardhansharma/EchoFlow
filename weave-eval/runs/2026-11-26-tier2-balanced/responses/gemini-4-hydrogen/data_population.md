Here is a horizontal bar chart and sortable table comparing the populations of the five countries in millions.

```weave
@weave 1 id: world_population title: "Population comparison"
root = Card(gap: m) [
  Title("Population by country", subtitle: "Values in millions · {totalPop | int}M combined", icon: "public"),
  BarChart(countries, x: "country", y: "pop", horizontal: true, h: m),
  Table(rows, cols: [col("Country", "country"), col("Pop (M)", "pop"), col("Share (%)", "sharePct")], sort: true)
]
countries = [{country: "India", pop: 1441}, {country: "China", pop: 1425}, {country: "USA", pop: 341}, {country: "Indonesia", pop: 279}, {country: "Pakistan", pop: 245}]
totalPop = sum(map(countries, c => c.pop))
rows = map(countries, c => {country: c.country, pop: c.pop, sharePct: round(c.pop * 100 / totalPop, 1)})
```
