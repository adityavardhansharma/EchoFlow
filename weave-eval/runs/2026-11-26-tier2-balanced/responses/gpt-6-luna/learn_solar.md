Try all five questions, then reveal the answers.

```weave
@weave 1 id: solar_system_quiz title: "Solar system quiz"
$show = false
root = Column(gap: m) [
  Title("Five questions"),
  Text("1. Which planet is largest?"),
  Text("2. Which planet is known for its prominent rings?"),
  Text("3. What is the name of Earth's natural satellite?"),
  Text("4. Which planet is closest to the Sun?"),
  Text("5. Which planet is often called the Red Planet?"),
  Button("Reveal answers", onTap: !toggle($show)),
  when($show, Card [
    Text("1. Jupiter. 2. Saturn. 3. The Moon. 4. Mercury. 5. Mars.")
  ])
]
```
