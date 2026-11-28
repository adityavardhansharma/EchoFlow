Here are your exam scores visualised as a chart and sortable table, with an overall average of 86.4%.

```weave
@weave 1 id: exam_scores title: "Exam scores"
root = Card(gap: m) [
  Title("Exam scores", subtitle: "5 subjects out of 100 each", icon: "grade"),
  Stat [
    Metric("Average score", avgScore, format: dec(1), suffix: "%", tone: primary),
    Metric("Total marks", totalMarks, format: int, suffix: "/500", tone: secondary),
    Metric("Highest", topSubj.score, format: int, suffix: "/100", tone: success)
  ],
  BarChart(scores, x: "subject", y: "score", horizontal: true, h: m),
  Table(scores, cols: [col("Subject", "subject"), col("Score", "score")], sort: true)
]
scores = [{subject: "Maths", score: 88}, {subject: "Physics", score: 76}, {subject: "Chemistry", score: 81}, {subject: "English", score: 92}, {subject: "Computer Science", score: 95}]
avgScore = avg(map(scores, s => s.score))
totalMarks = sum(map(scores, s => s.score))
topSubj = first(sortBy(scores, "score", desc: true))
```
