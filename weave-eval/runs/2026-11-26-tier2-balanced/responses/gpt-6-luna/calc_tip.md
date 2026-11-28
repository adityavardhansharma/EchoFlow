Change the bill or group size to update each person’s share.
```weave
@weave 1 id: tip title: "Tip splitter"
$bill = 2400
$people = 4
root = Card(gap: m) [Title("Split the bill"), Slider($bill, min: 100, max: 50000, label: "Bill", format: inr), Slider($people, min: 1, max: 20, step: 1, label: "People"), Metric("Per person", $bill * 1.1 / $people, format: inr)]
```
