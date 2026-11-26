Test your knowledge of the chemical symbols for the first 10 elements of the periodic table.

```weave
@weave 1 id: learn_elements title: "First 10 Elements Quiz"
$idx = 0
$picked = ""
$score = 0
$done = false
root = Card(gap: m) [
  Title("Periodic table: first 10", subtitle: $done ? "Quiz complete" : "Element {$idx + 1} of 10 • Score: {$score}", icon: "science"),
  Column(gap: m, show: not $done) [
    Text("Atomic number {cur.num}: What is the symbol for **{cur.name}**?", style: titleMedium),
    List(cur.opts, divider: false, render: opt => Button(opt, wide: true, variant: $picked == opt ? filled : outlined, tone: $picked == "" ? primary : (opt == cur.sym ? success : ($picked == opt ? error : neutral)), onTap: !if($picked == "", [!set($picked, opt), !if(opt == cur.sym, [!inc($score), !haptic(confirm)], !haptic(reject))]))),
    Callout($picked == cur.sym ? "Correct! {cur.name} is {cur.sym}." : "Not quite—{cur.name} has the symbol {cur.sym}.", kind: $picked == cur.sym ? success : error, show: $picked != ""),
    Button($idx < 9 ? "Next element" : "See score", wide: true, show: $picked != "", onTap: !if($idx < 9, [!inc($idx), !set($picked, ""), !haptic(tick)], [!set($done, true), !if($score >= 8, !confetti, !haptic(confirm))]))
  ],
  Column(gap: m, show: $done) [
    Metric("Final score", "{$score} / 10", tone: $score >= 8 ? success : primary),
    Callout($score == 10 ? "Perfect 10/10! You know all ten symbols." : "Great practice—try again to beat your score.", kind: $score >= 8 ? success : tip),
    Button("Try again", icon: "refresh", wide: true, onTap: [!set($idx, 0), !set($picked, ""), !set($score, 0), !set($done, false), !haptic(click)])
  ]
]
questions = [
  {id: 0, num: 1, name: "Hydrogen", sym: "H", opts: ["H", "Hy", "He", "Hg"]},
  {id: 1, num: 2, name: "Helium", sym: "He", opts: ["H", "He", "Hl", "Hm"]},
  {id: 2, num: 3, name: "Lithium", sym: "Li", opts: ["L", "Lt", "Li", "Lm"]},
  {id: 3, num: 4, name: "Beryllium", sym: "Be", opts: ["B", "Br", "By", "Be"]},
  {id: 4, num: 5, name: "Boron", sym: "B", opts: ["Bo", "B", "Br", "Bn"]},
  {id: 5, num: 6, name: "Carbon", sym: "C", opts: ["Ca", "Cb", "C", "Cr"]},
  {id: 6, num: 7, name: "Nitrogen", sym: "N", opts: ["N", "Ni", "Ng", "Nt"]},
  {id: 7, num: 8, name: "Oxygen", sym: "O", opts: ["Ox", "O", "Og", "Oy"]},
  {id: 8, num: 9, name: "Fluorine", sym: "F", opts: ["Fl", "Fr", "F", "Fu"]},
  {id: 9, num: 10, name: "Neon", sym: "Ne", opts: ["N", "No", "Nn", "Ne"]}
]
cur = find(questions, q => q.id == $idx)
```
