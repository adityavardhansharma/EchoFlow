Take this four-question quiz, then reveal the answer key.

```weave
@weave 1 id: india_freedom_quiz title: "India's freedom struggle"
$show = false
root = Column(gap: m) [
  Title("Four questions"),
  Text("1. In which year did the Indian National Congress form?"),
  Text("2. Which 1930 campaign included Gandhi's Salt March?"),
  Text("3. Who led the 1857 uprising in Jhansi?"),
  Text("4. On what date did India become independent?"),
  Button("Reveal answers", onTap: !toggle($show)),
  when($show, Card [
    Text("1. 1885. 2. Civil Disobedience Movement. 3. Rani Lakshmibai. 4. 15 August 1947.")
  ])
]
```
