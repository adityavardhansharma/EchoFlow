Practise 8 common Spanish greetings—tap any card to flip between Spanish and English.

```weave
@weave 1 id: learn_spanish title: "Spanish Greetings Flashcards"
$idx = 0
$flipped = false
$side = "es"
root = Card(gap: m) [
  Title("Spanish greetings", subtitle: "Card {$idx + 1} of 8", icon: "translate"),
  Segmented($side, options: ["es", "en"], labels: ["Spanish first", "English first"]),
  Card(style: tonal, pad: l, gap: s, onTap: [!toggle($flipped), !haptic(tick)]) [
    Text(showingSpanish ? "ESPAÑOL" : "ENGLISH", style: labelSmall, tone: primary),
    Text(showingSpanish ? cur.es : cur.en, style: headlineMedium),
    Column(gap: xs, show: $flipped) [
      Text(showingSpanish ? cur.en : cur.es, style: titleLarge, tone: secondary),
      Text(cur.note, style: bodyMedium, tone: muted)
    ],
    Text("Tap card or Flip to see translation", style: bodySmall, tone: muted, show: not $flipped)
  ],
  Row(gap: s, justify: between) [
    Button("Prev", icon: "arrow_back", variant: outlined, disabled: $idx == 0, onTap: [!dec($idx), !set($flipped, false), !haptic(tick)]),
    Button($flipped ? "Hide" : "Flip", icon: "flip", variant: tonal, onTap: [!toggle($flipped), !haptic(click)]),
    Button("Next", trailIcon: "arrow_forward", variant: filled, disabled: $idx == 7, onTap: [!inc($idx), !set($flipped, false), !haptic(tick)])
  ]
]
showingSpanish = ($side == "es" and not $flipped) or ($side == "en" and $flipped)
cards = [
  {id: 0, es: "¡Hola!", en: "Hello! / Hi!", note: "Universal greeting at any time of day."},
  {id: 1, es: "Buenos días", en: "Good morning", note: "Used from morning until around noon."},
  {id: 2, es: "Buenas tardes", en: "Good afternoon", note: "Used from noon until early evening."},
  {id: 3, es: "Buenas noches", en: "Good evening / Good night", note: "Used both to greet at night and say goodnight."},
  {id: 4, es: "¿Cómo estás?", en: "How are you?", note: "Informal; use '¿Cómo está?' to be formal."},
  {id: 5, es: "¿Qué tal?", en: "How's it going?", note: "Casual, friendly everyday check-in."},
  {id: 6, es: "Mucho gusto", en: "Nice to meet you", note: "Literally 'much pleasure' when introduced."},
  {id: 7, es: "¡Hasta luego!", en: "See you later!", note: "Warm, common goodbye."}
]
cur = find(cards, c => c.id == $idx)
on change($side) -> [!set($flipped, false), !haptic(tick)]
```
