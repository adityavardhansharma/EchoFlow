Here is your class grade distribution chart along with the overall pass percentage for grades A through D.

```weave
@weave 1 id: class_grades title: "Class grade distribution"
root = Card(gap: m) [
  Title("Class grade distribution", subtitle: "{totalStudents | int} students · A–D pass", icon: "school"),
  Stat [
    Metric("Pass rate", passPct, format: dec(1), suffix: "%", tone: success),
    Metric("Passed (A–D)", passed, format: int, tone: primary),
    Metric("Failed (F)", failed, format: int, tone: error)
  ],
  BarChart(grades, x: "grade", y: "count", h: m)
]
grades = [{grade: "A", count: 12}, {grade: "B", count: 18}, {grade: "C", count: 9}, {grade: "D", count: 4}, {grade: "F", count: 2}]
totalStudents = sum(map(grades, g => g.count))
passed = sum(map(filter(grades, g => g.grade != "F"), g => g.count))
failed = totalStudents - passed
passPct = round(passed * 100 / totalStudents, 1)
```
