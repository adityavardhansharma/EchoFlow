```weave
@weave 1 id: population title: "Population"
$data = [{country: "India", population: 1441}, {country: "China", population: 1425}, {country: "USA", population: 341}, {country: "Indonesia", population: 279}, {country: "Pakistan", population: 245}]
root = Card(gap: m) [Title("Population (millions)"), BarChart($data, x: "country", y: "population", horizontal: true), Table($data)]
```
