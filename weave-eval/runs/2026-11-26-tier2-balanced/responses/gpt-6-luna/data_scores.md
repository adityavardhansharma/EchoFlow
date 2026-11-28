Average score: **86.4**.
```weave
@weave 1 id: scores title: "Exam scores"
$data = [{subject: "Maths", score: 88}, {subject: "Physics", score: 76}, {subject: "Chemistry", score: 81}, {subject: "English", score: 92}, {subject: "Computer Science", score: 95}]
root = Card(gap: m) [Title("Exam results"), BarChart($data, x: "subject", y: "score"), Table($data), Metric("Average", avg(map($data, x => x.score)), format: dec(1))]
```
