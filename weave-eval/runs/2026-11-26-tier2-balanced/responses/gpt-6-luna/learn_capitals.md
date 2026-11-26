Fill in each blank, then check your answers.

```weave
@weave 1 id: india_capitals title: "Indian state capitals"
$answers = ["", "", "", "", ""]
$show = false
root = Column(gap: m) [
  Title("Fill in the capitals", subtitle: "Type your answers, then reveal the key"),
  TextField($answers[0], label: "1. Gujarat"),
  TextField($answers[1], label: "2. Assam"),
  TextField($answers[2], label: "3. Kerala"),
  TextField($answers[3], label: "4. Rajasthan"),
  TextField($answers[4], label: "5. Odisha"),
  Button("Show answers", onTap: !toggle($show)),
  when($show, Card [
    Text("Gandhinagar · Dispur · Thiruvananthapuram · Jaipur · Bhubaneswar")
  ])
]
```
