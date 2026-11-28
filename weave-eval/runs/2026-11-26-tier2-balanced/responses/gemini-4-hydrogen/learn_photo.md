Test what you know with this 3-question quiz on photosynthesis.

```weave
@weave 1 id: learn_photo title: "Photosynthesis Quiz"
$idx = 0
$picked = ""
$score = 0
$done = false
root = Card(gap: m) [
  Title("Photosynthesis quiz", subtitle: $done ? "Results" : "Question {$idx + 1} of 3 • Score: {$score}", icon: "eco"),
  Column(gap: m, show: not $done) [
    Text(cur.q, style: titleMedium),
    List(cur.opts, divider: false, render: opt => Button(opt, wide: true, variant: $picked == opt ? filled : outlined, tone: $picked == "" ? primary : (opt == cur.ans ? success : ($picked == opt ? error : neutral)), onTap: !if($picked == "", [!set($picked, opt), !if(opt == cur.ans, [!inc($score), !haptic(confirm)], !haptic(reject))]))),
    Callout(cur.note, kind: $picked == cur.ans ? success : info, title: $picked == cur.ans ? "Correct" : "Answer: {cur.ans}", show: $picked != ""),
    Button($idx < 2 ? "Next question" : "See score", wide: true, show: $picked != "", onTap: !if($idx < 2, [!inc($idx), !set($picked, ""), !haptic(tick)], [!set($done, true), !if($score == 3, !confetti, !haptic(confirm))]))
  ],
  Column(gap: m, show: $done) [
    Metric("Score", "{$score} / 3", tone: $score == 3 ? success : primary),
    Callout($score == 3 ? "3/3! You nailed the core inputs, organelle, and products." : "Good effort—give it another run to lock it in.", kind: $score == 3 ? success : tip),
    Button("Restart quiz", icon: "refresh", wide: true, onTap: [!set($idx, 0), !set($picked, ""), !set($score, 0), !set($done, false), !haptic(click)])
  ]
]
questions = [
  {id: 0, q: "Where inside a plant cell does photosynthesis take place?", ans: "Chloroplast", opts: ["Chloroplast", "Mitochondrion", "Nucleus", "Ribosome"], note: "Chloroplasts contain chlorophyll, which captures sunlight to power photosynthesis."},
  {id: 1, q: "Which gas do plants absorb from the air for photosynthesis?", ans: "Carbon dioxide", opts: ["Carbon dioxide", "Oxygen", "Nitrogen", "Methane"], note: "Plants take in CO₂ through tiny leaf pores called stomata."},
  {id: 2, q: "What are the two main products of photosynthesis?", ans: "Glucose and oxygen", opts: ["Glucose and oxygen", "Carbon dioxide and water", "Nitrogen and starch", "Water and ATP"], note: "Using light energy, 6CO₂ + 6H₂O becomes C₆H₁₂O₆ (glucose) + 6O₂ (oxygen)."}
]
cur = find(questions, q => q.id == $idx)
```
