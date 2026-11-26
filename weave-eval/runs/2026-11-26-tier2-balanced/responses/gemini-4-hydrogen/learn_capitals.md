Fill in the capital city for each of the five Indian states, then tap **Check answers**.

```weave
@weave 1 id: learn_capitals title: "Indian State Capitals"
$a1 = ""
$a2 = ""
$a3 = ""
$a4 = ""
$a5 = ""
$checked = false
root = Card(gap: m) [
  Title("Fill in the blanks", subtitle: "Capitals of 5 Indian states", icon: "map"),
  TextField($a1, label: "1. Maharashtra", placeholder: "Capital city"),
  Text("Correct!" , style: labelSmall, tone: success, show: $checked and ok1),
  Text("Answer: Mumbai", style: labelSmall, tone: error, show: $checked and not ok1),
  TextField($a2, label: "2. Rajasthan", placeholder: "Capital city"),
  Text("Correct!", style: labelSmall, tone: success, show: $checked and ok2),
  Text("Answer: Jaipur", style: labelSmall, tone: error, show: $checked and not ok2),
  TextField($a3, label: "3. Tamil Nadu", placeholder: "Capital city"),
  Text("Correct!", style: labelSmall, tone: success, show: $checked and ok3),
  Text("Answer: Chennai", style: labelSmall, tone: error, show: $checked and not ok3),
  TextField($a4, label: "4. Bihar", placeholder: "Capital city"),
  Text("Correct!", style: labelSmall, tone: success, show: $checked and ok4),
  Text("Answer: Patna", style: labelSmall, tone: error, show: $checked and not ok4),
  TextField($a5, label: "5. West Bengal", placeholder: "Capital city"),
  Text("Correct!", style: labelSmall, tone: success, show: $checked and ok5),
  Text("Answer: Kolkata", style: labelSmall, tone: error, show: $checked and not ok5),
  Callout("You scored {score} out of 5!", kind: score == 5 ? success : info, show: $checked),
  Row(gap: s) [
    Button("Check answers", icon: "check_circle", wide: true, onTap: [!set($checked, true), !if(score == 5, !confetti, !haptic(confirm))]),
    Button("Reset", variant: outlined, onTap: [!set($a1, ""), !set($a2, ""), !set($a3, ""), !set($a4, ""), !set($a5, ""), !set($checked, false), !haptic(click)])
  ]
]
fn match(val, ans) = replace(lower(val), " ", "") == ans
ok1 = match($a1, "mumbai")
ok2 = match($a2, "jaipur")
ok3 = match($a3, "chennai")
ok4 = match($a4, "patna")
ok5 = match($a5, "kolkata")
score = (ok1 ? 1 : 0) + (ok2 ? 1 : 0) + (ok3 ? 1 : 0) + (ok4 ? 1 : 0) + (ok5 ? 1 : 0)
```
