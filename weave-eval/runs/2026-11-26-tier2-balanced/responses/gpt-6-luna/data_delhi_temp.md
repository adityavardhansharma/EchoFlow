Delhi’s average monthly maximum temperatures peak in **May at 40°C**.
```weave
@weave 1 id: delhi_temp title: "Delhi monthly highs"
$data = [{month: "Jan", temp: 21}, {month: "Feb", temp: 24}, {month: "Mar", temp: 30}, {month: "Apr", temp: 36}, {month: "May", temp: 40}, {month: "Jun", temp: 39}, {month: "Jul", temp: 35}, {month: "Aug", temp: 34}, {month: "Sep", temp: 34}, {month: "Oct", temp: 33}, {month: "Nov", temp: 28}, {month: "Dec", temp: 23}]
root = Card(gap: m) [Title("Average monthly maximum", subtitle: "Hottest: May · 40°C"), LineChart($data, x: "month", y: "temp", h: m)]
```
