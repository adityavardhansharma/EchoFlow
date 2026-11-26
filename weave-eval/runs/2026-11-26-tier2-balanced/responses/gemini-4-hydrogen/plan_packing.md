Here is an interactive packing checklist for a 5-day beach trip that you can tick off as you pack.

```weave
@weave 1 id: beach_packing title: "5-day beach packing list"
$cat = "All"
$items = [
  {id: 0, name: "5 breathable T-shirts / tops", cat: "Clothes", done: false},
  {id: 1, name: "3 pairs of shorts / skirts", cat: "Clothes", done: false},
  {id: 2, name: "2 swimsuits & cover-up", cat: "Clothes", done: false},
  {id: 3, name: "Light evening outfit & jacket", cat: "Clothes", done: false},
  {id: 4, name: "Flip-flops & walking sandals", cat: "Clothes", done: false},
  {id: 5, name: "Quick-dry beach towel", cat: "Beach", done: false},
  {id: 6, name: "Sunglasses & wide-brim hat", cat: "Beach", done: false},
  {id: 7, name: "Waterproof phone pouch & dry bag", cat: "Beach", done: false},
  {id: 8, name: "SPF 50+ reef-safe sunscreen", cat: "Care", done: false},
  {id: 9, name: "After-sun aloe vera & lip balm", cat: "Care", done: false},
  {id: 10, name: "Toiletries & basic first-aid meds", cat: "Care", done: false},
  {id: 11, name: "Power bank, chargers & earbuds", cat: "Tech", done: false},
  {id: 12, name: "ID, booking confirmations & cash", cat: "Tech", done: false}
]
root = Card(gap: m) [
  Title("5-day beach packing list", subtitle: "{packedCount} of {totalCount} items packed", icon: "beach_access"),
  Stat [
    Metric("Packed", packedCount, format: int, suffix: "/{totalCount}", tone: success),
    Metric("Left to pack", totalCount - packedCount, format: int, tone: tertiary)
  ],
  Segmented($cat, options: ["All", "Clothes", "Beach", "Care", "Tech"]),
  List(shown, render: item => Row(justify: between, align: center) [
    Column(gap: xxs, w: 3/4) [
      Text(item.name, style: bodyMedium, strike: item.done, tone: item.done ? muted : neutral),
      Text(item.cat, style: labelSmall, tone: muted)
    ],
    Button(item.done ? "Packed" : "Pack", icon: item.done ? "check_box" : "check_box_outline_blank", variant: item.done ? tonal : outlined, tone: item.done ? success : primary, size: xs, onTap: [!set($items, map($items, x => x.id == item.id ? merge(x, {done: not x.done}) : x)), !haptic(click)])
  ]),
  Button("Reset checklist", icon: "refresh", variant: text, onTap: [!reset($items), !haptic(tick)])
]
packedCount = len(filter($items, x => x.done))
totalCount = len($items)
shown = $cat == "All" ? $items : filter($items, x => x.cat == $cat)
on change($items) -> !if(packedCount == totalCount, !confetti)
```
