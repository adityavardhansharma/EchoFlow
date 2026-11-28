Try naming the symbol for each element before opening the answer key.

```weave
@weave 1 id: element_symbols title: "First 10 element symbols"
$show = false
root = Column(gap: m) [
  Title("Element symbol quiz", subtitle: "What is the symbol for each?"),
  List(items: ["Hydrogen", "Helium", "Lithium", "Beryllium", "Boron", "Carbon", "Nitrogen", "Oxygen", "Fluorine", "Neon"]),
  Button("Show answer key", onTap: !toggle($show)),
  when($show, Card [
    Text("Hydrogen H · Helium He · Lithium Li · Beryllium Be · Boron B · Carbon C · Nitrogen N · Oxygen O · Fluorine F · Neon Ne")
  ])
]
```
