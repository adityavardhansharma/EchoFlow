Flip through eight common Spanish greetings.

```weave
@weave 1 id: spanish_greetings title: "Spanish greeting flashcards"
$card = 0
root = Card(gap: m) [
  Title("Greeting flashcards", subtitle: "Card {$card + 1} of 8"),
  Text(["Hola — Hello.", "Buenos días — Good morning.", "Buenas tardes — Good afternoon.", "Buenas noches — Good evening / good night.", "¿Cómo estás? — How are you? (informal)", "¿Cómo está? — How are you? (formal)", "Mucho gusto — Nice to meet you.", "Hasta luego — See you later."][$card]),
  Button("Next card", onTap: !set($card, ($card + 1) % 8), icon: "arrow_forward")
]
```
