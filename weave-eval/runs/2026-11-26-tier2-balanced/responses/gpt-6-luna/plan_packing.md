Tap an item as you pack it.
```weave
@weave 1 id: beach_pack title: "Beach trip packing"
$packed = 0
root = Card(gap: s) [Title("5-day beach checklist"), Text("Checked off: {$packed} items"), Button("Swimwear (2–3 sets)", onTap: !inc($packed)), Button("Light outfits and evening layer", onTap: !inc($packed)), Button("Sandals and walking shoes", onTap: !inc($packed)), Button("Sunscreen and after-sun lotion", onTap: !inc($packed)), Button("Hat and sunglasses", onTap: !inc($packed)), Button("Beach towel and beach bag", onTap: !inc($packed)), Button("Toiletries and medications", onTap: !inc($packed)), Button("Phone, charger and power bank", onTap: !inc($packed)), Button("Reusable water bottle", onTap: !inc($packed)), Button("Travel documents and wallet", onTap: !inc($packed))]
```
