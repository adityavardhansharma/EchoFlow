Here is a balanced 5-day exam study timetable covering 5 subjects for 6 hours a day (6 hours per subject total).

```weave
@weave 1 id: study_timetable title: "Exam week timetable"
$day = 0
root = Card(gap: m) [
  Title("Exam week study plan", subtitle: "5 subjects · 5 days · 6 hrs/day (30 hrs total)", icon: "menu_book"),
  Stat [
    Metric("Daily study", 6, format: int, suffix: " hrs", tone: primary),
    Metric("Per subject", 6, format: int, suffix: " hrs", tone: secondary),
    Metric("Weekly total", 30, format: int, suffix: " hrs", tone: success)
  ],
  Tabs($day, labels: ["Day 1", "Day 2", "Day 3", "Day 4", "Day 5"], scrollable: auto) [
    studyDay("Maths", "Core concepts & formulas", "Physics", "Mechanics & numericals", "English", "Reading & essay outlines"),
    studyDay("Chemistry", "Organic reactions & equations", "Comp Sci", "Algorithms & coding drills", "Maths", "Problem sets & proofs"),
    studyDay("Physics", "Optics & electricity", "English", "Literature & grammar", "Chemistry", "Physical & inorganic theory"),
    studyDay("Comp Sci", "Data structures & SQL", "Maths", "Timed mock paper", "Physics", "Past-year numericals"),
    studyDay("Chemistry", "Formula & reaction recap", "Comp Sci", "Full practice test", "English", "Past paper & final review")
  ],
  Button("Use my subjects", icon: "edit", variant: tonal, onTap: !say("Update this 5-day, 6-hour/day study timetable using my actual exam subjects"))
]
fn studyDay(s1, d1, s2, d2, s3, d3) = Column(gap: s) [
  Item(s1, subtitle: d1, overline: "9:00 – 11:00 AM · 2 hrs"),
  Item(s2, subtitle: d2, overline: "11:30 AM – 1:30 PM · 2 hrs"),
  Item(s3, subtitle: d3, overline: "3:30 – 5:30 PM · 2 hrs")
]
on change($day) -> !haptic(tick)
```
