Here is a 4-question quiz on key moments in India's freedom struggle.

```weave
@weave 1 id: learn_freedom title: "India's Freedom Struggle"
$idx = 0
$picked = ""
$score = 0
$done = false
root = Card(gap: m) [
  Title("India's freedom struggle", subtitle: $done ? "Results" : "Question {$idx + 1} of 4 • Score: {$score}", icon: "flag"),
  Column(gap: m, show: not $done) [
    Text(cur.q, style: titleMedium),
    List(cur.opts, divider: false, render: opt => Button(opt, wide: true, variant: $picked == opt ? filled : outlined, tone: $picked == "" ? primary : (opt == cur.ans ? success : ($picked == opt ? error : neutral)), onTap: !if($picked == "", [!set($picked, opt), !if(opt == cur.ans, [!inc($score), !haptic(confirm)], !haptic(reject))]))),
    Callout(cur.fact, kind: $picked == cur.ans ? success : info, title: $picked == cur.ans ? "Correct" : "Answer: {cur.ans}", show: $picked != ""),
    Button($idx < 3 ? "Next question" : "See results", wide: true, show: $picked != "", onTap: !if($idx < 3, [!inc($idx), !set($picked, ""), !haptic(tick)], [!set($done, true), !if($score == 4, !confetti, !haptic(confirm))]))
  ],
  Column(gap: m, show: $done) [
    Metric("Score", "{$score} / 4", tone: $score >= 3 ? success : primary),
    Callout($score == 4 ? "4/4! Spot on across every milestone." : "Well played—review any missed dates and try again.", kind: $score == 4 ? success : tip),
    Button("Play again", icon: "refresh", wide: true, onTap: [!set($idx, 0), !set($picked, ""), !set($score, 0), !set($done, false), !haptic(click)])
  ]
]
questions = [
  {id: 0, q: "Who gave the 'Do or Die' call during the 1942 Quit India Movement?", ans: "Mahatma Gandhi", opts: ["Mahatma Gandhi", "Subhas Chandra Bose", "Bhagat Singh", "Jawaharlal Nehru"], fact: "Mahatma Gandhi gave this speech at Gowalia Tank Maidan in Bombay on 8 August 1942."},
  {id: 1, q: "In which year did the Jallianwala Bagh massacre take place?", ans: "1919", opts: ["1915", "1919", "1922", "1930"], fact: "It occurred on 13 April 1919 in Amritsar during Baisakhi."},
  {id: 2, q: "In which year did the Dandi Salt March take place?", ans: "1930", opts: ["1920", "1928", "1930", "1942"], fact: "Gandhi marched from Sabarmati Ashram to Dandi in March–April 1930 to defy the British salt tax."},
  {id: 3, q: "Who was the first President of the Indian National Congress in 1885?", ans: "W. C. Bonnerjee", opts: ["Dadabhai Naoroji", "W. C. Bonnerjee", "A. O. Hume", "Surendranath Banerjee"], fact: "Womesh Chunder Bonnerjee presided over the first INC session in Bombay in December 1885."}
]
cur = find(questions, q => q.id == $idx)
```
