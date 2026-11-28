Here is a trip fuel cost calculator set to 15 km/L and ₹105/L petrol, with an adjustable distance slider.

```weave
@weave 1 id: fuel_calc title: "Trip fuel cost"
$distance = 350
$mileage = 15
$price = 105
root = Card(gap: m) [
  Title("Trip fuel cost", subtitle: "{$distance} km at {$mileage} km/L · ₹{$price}/L", icon: "local_gas_station"),
  Slider($distance, min: 10, max: 1500, step: 10, label: "Trip distance", suffix: " km"),
  Slider($mileage, min: 5, max: 35, step: 0.5, label: "Mileage", suffix: " km/L"),
  Slider($price, min: 80, max: 130, step: 0.5, label: "Petrol price", prefix: "₹", suffix: "/L"),
  Stat [
    Metric("Fuel cost", cost, format: inr, tone: primary),
    Metric("Fuel needed", litres, format: dec(1), suffix: " L", tone: secondary),
    Metric("Cost per km", perKm, format: num(2), prefix: "₹", tone: muted)
  ],
  Callout("Round trip ({$distance * 2} km) costs **{cost * 2 | inr}** for **{litres * 2 | dec(1)} L** of petrol.", kind: info)
]
litres = $distance / $mileage
cost = litres * $price
perKm = $price / $mileage
on change($distance) -> !haptic(tick)
```
