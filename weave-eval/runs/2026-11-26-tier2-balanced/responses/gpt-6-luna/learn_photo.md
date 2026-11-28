Answer these three questions, then check the key.

```weave
@weave 1 id: photosynthesis_quiz title: "Photosynthesis"
$show = false
root = Column(gap: m) [
  Title("Three-question quiz"),
  Text("1. Which pigment captures much of the light energy?"),
  Text("2. Which gas do plants take in for photosynthesis?"),
  Text("3. What sugar do plants make?"),
  Button("Reveal answers", onTap: !toggle($show)),
  when($show, Card [
    Text("1. Chlorophyll. 2. Carbon dioxide. 3. Glucose.")
  ])
]
```
