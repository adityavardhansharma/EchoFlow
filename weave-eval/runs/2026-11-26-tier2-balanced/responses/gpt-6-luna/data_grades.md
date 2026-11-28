The pass rate is **95.6%** (43 of 45 students).
```weave
@weave 1 id: grades title: "Grade distribution"
$data = [{grade: "A", count: 12}, {grade: "B", count: 18}, {grade: "C", count: 9}, {grade: "D", count: 4}, {grade: "F", count: 2}]
root = Card(gap: m) [Title("Class grades"), BarChart($data, x: "grade", y: "count"), Metric("Pass rate", 43 / 45, format: percent)]
```
