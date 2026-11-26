Here is a 5-question quiz on the solar system.

```weave
@weave 1 id: learn_solar title: "Solar System Quiz"
$idx = 0
$picked = ""
$score = 0
$done = false
root = Card(gap: m) [
  Title("Solar system quiz", subtitle: $done ? "Final results" : "Question {$idx + 1} of 5 • Score: {$score}", icon: "public"),
  Column(gap: m, show: not $done) [
    Text(cur.q, style: titleMedium),
    List(cur.opts, divider: false, render: opt => Button(opt, wide: true, variant: $picked == opt ? filled : outlined, tone: $picked == "" ? primary : (opt == cur.ans ? success : ($picked == opt ? error : neutral)), onTap: !if($picked == "", [!set($picked, opt), !if(opt == cur.ans, [!inc($score), !haptic(confirm)], !haptic(reject))]))),
    Callout(cur.fact, kind: $picked == cur.ans ? success : info, title: $picked == cur.ans ? "Correct" : "Answer: {cur.ans}", show: $picked != ""),
    Button($idx < 4 ? "Next question" : "Finish quiz", wide: true, show: $picked != "", onTap: !if($idx < 4, [!inc($idx), !set($picked, ""), !haptic(tick)], [!set($done, true), !if($score >= 4, !confetti, !haptic(confirm))]))
  ],
  Column(gap: m, show: $done) [
    Metric("Score", "{$score} / 5", tone: $score >= 4 ? success : primary),
    Callout($score == 5 ? "5/5! Stellar knowledge of the solar system." : "Nice work—tap Play again for another round.", kind: $score >= 4 ? success : tip),
    Button("Play again", icon: "refresh", wide: true, onTap: [!set($idx, 0), !set($picked, ""), !set($score, 0), !set($done, false), !haptic(click)])
  ]
]
questions = [
  {id: 0, q: "Which planet is closest to the Sun?", ans: "Mercury", opts: ["Mercury", "Venus", "Mars", "Earth"], fact: "Mercury orbits at an average distance of 57.9 million km—about 0.39 AU."},
  {id: 1, q: "Which is the largest planet in our solar system?", ans: "Jupiter", opts: ["Jupiter", "Saturn", "Neptune", "Uranus"], fact: "Jupiter is more than twice as massive as all the other planets combined."},
  {id: 2, q: "Which planet is famous for its bright icy ring system?", ans: "Saturn", opts: ["Saturn", "Uranus", "Mars", "Venus"], fact: "Saturn's main rings span hundreds of thousands of kilometres across yet are mostly under 1 km thick."},
  {id: 3, q: "Which planet is the hottest due to a thick CO₂ greenhouse atmosphere?", ans: "Venus", opts: ["Venus", "Mercury", "Mars", "Jupiter"], fact: "Surface temperatures on Venus reach around 465 °C, hotter than Mercury."},
  {id: 4, q: "Roughly how long does sunlight take to reach Earth?", ans: "About 8 minutes", opts: ["About 8 seconds", "About 8 minutes", "About 1 hour", "About 1 day"], fact: "Light takes roughly 8 minutes and 20 seconds to travel 150 million km from the Sun."}
]
cur = find(questions, q => q.id == $idx)
```
