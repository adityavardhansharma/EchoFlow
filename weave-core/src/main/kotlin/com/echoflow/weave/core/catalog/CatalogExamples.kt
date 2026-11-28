package com.echoflow.weave.core.catalog

/**
 * One short example per component and mark, returned by the `weave_docs` tool after each
 * signature line (06 §8.2). An example is one usage line, preceded by the state and data lines it
 * needs; `CatalogExamplesTest` compiles every one at tier 4 with no errors.
 *
 * Marks are keyed `Container.name` (`Chart.line`, `Canvas.circle`).
 */
object CatalogExamples {

    private const val SALES = """sales = [{month: "Jan", total: 80}, {month: "Feb", total: 120}, {month: "Mar", total: 95}]"""
    private const val SHARE = """spend = [{label: "Rent", value: 18000}, {label: "Food", value: 9000}, {label: "Travel", value: 4000}]"""

    val components: Map<String, String> = mapOf(
        // layout
        "Column" to """Column(gap: m) [Text("First"), Text("Second")]""",
        "Row" to """Row(gap: s) [Icon("star"), Text("4.8 rating")]""",
        "Box" to """Box(contentAlign: center, h: s) [Text("Centred")]""",
        "Grid" to """Grid(cols: 2) [Metric("Steps", 8432), Metric("Floors", 12)]""",
        "Flow" to """Flow [Chip("Vegan"), Chip("Gluten free"), Chip("Spicy")]""",
        "Carousel" to """Carousel [Card [Text("Day 1")], Card [Text("Day 2")]]""",
        "Spacer" to """Spacer(size: l)""",
        "Divider" to """Divider(inset: true)""",
        "Card" to """Card(style: outlined, gap: s) [Title("Summary"), Text("All good")]""",
        "Surface" to """Surface(tone: primary, pad: m) [Text("Tip: drink water")]""",
        "Section" to """Section(title: "Details", collapsible: true) [Text("More info")]""",
        "Split" to """Split(ratio: 0.5) [Text("Left"), Text("Right")]""",
        "Sticky" to """Sticky(h: l) [Title("Header"), Text("Scrolling body")]""",
        // text
        "Text" to """Text("Hello **world**", style: bodyLarge)""",
        "Title" to """Title("Budget", subtitle: "November", icon: "savings")""",
        "Rich" to """Rich [Text("Total: "), Text("1,200", weight: bold)]""",
        "Markdown" to """Markdown("**Note:** prices include tax")""",
        "Icon" to """Icon("favorite", size: l, tone: error)""",
        "Avatar" to """Avatar("Asha Rao")""",
        "Badge" to """Badge(3, tone: error)""",
        "Chip" to """Chip("Vegan", icon: "eco", selected: true)""",
        "Link" to """Link("Docs", "https://example.com")""",
        "Quote" to """Quote("Simplicity is the soul of efficiency.", cite: "Austin Freeman")""",
        "Code" to """Code("print('hi')", lang: "python")""",
        "Kbd" to """Kbd(["Ctrl", "C"])""",
        "Empty" to """Empty("No tasks yet", body: "Add one to get started")""",
        "Callout" to """Callout("Prices change daily", kind: warning)""",
        // numbers
        "Metric" to """Metric("Monthly EMI", 43391, format: inr)""",
        "Stat" to """Stat [Metric("Calories", 1820), Metric("Protein", 96, suffix: " g")]""",
        "Progress" to """Progress(0.65, label: "Saved", showValue: true)""",
        "Gauge" to """Gauge(72, label: "Score")""",
        "Rating" to """Rating(4.5)""",
        "Steps" to """Steps(1, ["Cart", "Address", "Pay"])""",
        "Timer" to "\$running = false\n" + """Timer(300, ${'$'}running, onDone: !toast("Time's up"))""",
        "Clock" to """Clock(style: analog)""",
        // inputs
        "Button" to "\$count = 0\n" + """Button("Add one", onTap: !inc(${'$'}count), icon: "add")""",
        "IconButton" to "\$count = 0\n" + """IconButton("refresh", onTap: !reset(${'$'}count), label: "Reset")""",
        "Toggle" to "\$wifi = true\n" + """Toggle(${'$'}wifi, label: "Wi-Fi")""",
        "Check" to "\$packed = false\n" + """Check(${'$'}packed, label: "Packed the charger")""",
        "Radio" to "\$size = \"M\"\n" + """Radio(${'$'}size, options: ["S", "M", "L"])""",
        "Segmented" to "\$years = 20\n" + """Segmented(${'$'}years, options: [10, 20, 30], suffix: " yrs")""",
        "Chips" to "\$tags = []\n" + """Chips(${'$'}tags, options: ["Veg", "Spicy", "Quick"], multi: true)""",
        "Slider" to "\$rate = 8.5\n" + """Slider(${'$'}rate, min: 6, max: 14, step: 0.05, label: "Interest rate", suffix: "%")""",
        "Stepper" to "\$guests = 2\n" + """Stepper(${'$'}guests, min: 1, max: 10, label: "Guests")""",
        "TextField" to "\$name = \"\"\n" + """TextField(${'$'}name, label: "Your name")""",
        "Select" to "\$city = \"Pune\"\n" + """Select(${'$'}city, options: ["Pune", "Delhi", "Goa"], label: "City")""",
        "Form" to "\$guests = 2\n" + """Form(onSubmit: !say("Book a table for {${'$'}guests}")) [Stepper(${'$'}guests, min: 1, max: 10, label: "Guests")]""",
        "Fab" to "\$items = []\n" + """Fab("add", onTap: !push(${'$'}items, "New item"), label: "Add")""",
        "SplitButton" to """SplitButton("Save", onTap: !toast("Saved"), menu: [{label: "Save a copy", onTap: !toast("Copied")}])""",
        "ButtonGroup" to "\$view = \"Day\"\n" + """ButtonGroup(${'$'}view, options: ["Day", "Week", "Month"])""",
        "RangeSlider" to "\$price = [500, 2000]\n" + """RangeSlider(${'$'}price, min: 0, max: 5000, step: 100, format: inr, label: "Price")""",
        "DatePicker" to "\$day = today()\n" + """DatePicker(${'$'}day, label: "Check-in")""",
        "DateRange" to "\$stay = [today(), addDays(today(), 3)]\n" + """DateRange(${'$'}stay, label: "Stay")""",
        "TimePicker" to "\$alarm = time(\"09:30\")\n" + """TimePicker(${'$'}alarm, label: "Alarm")""",
        "ColorPick" to "\$accent = \"primary\"\n" + """ColorPick(${'$'}accent)""",
        "RatingInput" to "\$stars = 0\n" + """RatingInput(${'$'}stars)""",
        "Pad2D" to "\$taste = {x: 0.5, y: 0.5}\n" + """Pad2D(${'$'}taste, x: {min: 0, max: 1, label: "Sweet"}, y: {min: 0, max: 1, label: "Sour"})""",
        "Sketch" to "\$drawing = null\n" + """Sketch(${'$'}drawing)""",
        "Swipe" to "\$deck = [\"Goa\", \"Manali\", \"Ooty\"]\n" + """Swipe(${'$'}deck, render: i => Card [Title(i)], onRight: i => !toast("Saved {i}"))""",
        "Rank" to "\$order = [\"Rent\", \"Food\", \"Travel\"]\n" + """Rank(${'$'}order, render: i => Text(i))""",
        "Sort" to "\$board = {Todo: [\"Write spec\"], Doing: [], Done: []}\n" + """Sort(${'$'}board, columns: ["Todo", "Doing", "Done"], render: i => Text(i))""",
        // data
        "List" to """List([{title: "Pay rent", due: "Mon"}, {title: "Gym", due: "Tue"}], render: t => Item(t.title, subtitle: t.due))""",
        "Item" to """Item("Pay rent", subtitle: "Due Monday", lead: "home")""",
        "Table" to """Table([{city: "Pune", temp: 24}, {city: "Delhi", temp: 18}], cols: [col("City", "city"), col("°C", "temp", align: end)])""",
        "Tree" to """Tree({name: "src", kids: [{name: "main.kt", kids: []}]}, children: n => n.kids, render: n => Text(n.name))""",
        "Timeline" to """Timeline([{t: "9:00", what: "Amber Fort"}, {t: "13:00", what: "Lunch"}], time: i => i.t, render: i => Text(i.what))""",
        "Calendar" to """Calendar([{date: date("2026-12-05"), title: "Flight"}])""",
        "Heatcal" to """Heatcal([0, 2, 5, 1, 0, 3, 4])""",
        "Diff" to """Diff("the quick fox", "the slow fox")""",
        "Json" to """Json({name: "Asha", tags: ["a", "b"]})""",
        // navigation
        "Tabs" to "\$tab = 0\n" + """Tabs(${'$'}tab, labels: ["Plan", "Costs"]) [Text("The plan"), Text("The costs")]""",
        "Pager" to "\$page = 0\n" + """Pager(${'$'}page) [Card [Text("One")], Card [Text("Two")]]""",
        "Wizard" to "\$step = 0\n" + """Wizard(${'$'}step, labels: ["Who", "When"], onFinish: !say("Book it")) [Text("Who is coming?"), Text("Pick a date")]""",
        "Accordion" to """Accordion [Section(title: "Fees") [Text("No joining fee")], Section(title: "Rewards") [Text("2% back")]]""",
        "Reveal" to """Reveal(lines: 2) [Text("A long paragraph that is clamped until the user taps Show more.")]""",
        "Flip" to """Flip(back: Text("Bonjour")) [Text("Hello")]""",
        // media
        "Image" to """Image("https://example.com/fort.jpg", "Amber Fort at sunset", h: l)""",
        "Gallery" to """Gallery(["https://example.com/a.jpg", "https://example.com/b.jpg"])""",
        "Video" to """Video("https://example.com/clip.mp4")""",
        "Audio" to """Audio("https://example.com/talk.mp3", title: "Episode 1")""",
        "Map" to """Map(center: "Jaipur", markers: [{lat: 26.92, lng: 75.82, title: "Hawa Mahal"}])""",
        "Model3D" to """Model3D("https://example.com/chair.glb")""",
        "Lottie" to """Lottie("https://example.com/check.json")""",
        "Qr" to """Qr("https://example.com")""",
        // overlays
        "Sheet" to """details = Sheet(title: "Details") [Text("More about this plan")]""" + "\n" +
            """Button("Details", onTap: !open(details))""",
        "Dialog" to "\$items = [\"Milk\"]\n" + """clear_all = Dialog("Clear the list?", confirm: {label: "Clear", onTap: !set(${'$'}items, [])}) [Text("This can't be undone.")]""" + "\n" +
            """Button("Clear", onTap: !open(clear_all))""",
        "Popover" to """info = Button("Info", onTap: !open(tip), variant: text)""" + "\n" +
            """tip = Popover(info) [Text("Prices include GST")]""" + "\n" + """Column [info]""",
        "Menu" to """more = IconButton("more_vert", onTap: !open(actions), label: "More")""" + "\n" +
            """actions = Menu(more, items: [{label: "Copy", icon: "content_copy", onTap: !copy("Hello")}])""" + "\n" + """Column [more]""",
        // charts
        "Chart" to "$SALES\n" + """Chart(sales, h: m) [line(x: "month", y: "total", tone: primary), rule(y: 100, label: "Target")]""",
        "LineChart" to "$SALES\n" + """LineChart(sales, x: "month", y: "total")""",
        "AreaChart" to "$SALES\n" + """AreaChart(sales, x: "month", y: "total")""",
        "BarChart" to "$SALES\n" + """BarChart(sales, x: "month", y: "total")""",
        "PieChart" to "$SHARE\n" + """PieChart(spend, value: "value", label: "label")""",
        "DonutChart" to "$SHARE\n" + """DonutChart(spend, value: "value", label: "label")""",
        "ScatterChart" to """ScatterChart([{h: 160, w: 55}, {h: 175, w: 72}], x: "h", y: "w")""",
        "Sparkline" to """Sparkline([3, 5, 4, 8, 6])""",
        "Histogram" to """Histogram([2, 3, 3, 4, 5, 5, 5, 7], bins: 4)""",
        "Radar" to """Radar([{skill: "Speed", score: 7}, {skill: "Power", score: 5}, {skill: "Skill", score: 8}], axes: ["Speed", "Power", "Skill"])""",
        "Funnel" to """Funnel([{stage: "Visits", n: 1000}, {stage: "Sign-ups", n: 240}, {stage: "Paid", n: 40}], value: "n", label: "stage")""",
        "Treemap" to "$SHARE\n" + """Treemap(spend, value: "value", label: "label")""",
        "Sankey" to """Sankey([{a: "Salary", b: "Rent", v: 18000}, {a: "Salary", b: "Savings", v: 12000}], from: "a", to: "b", value: "v")""",
        "Candles" to """Candles([{d: "Mon", o: 10, hi: 12, lo: 9, c: 11}, {d: "Tue", o: 11, hi: 13, lo: 10, c: 12}], x: "d", open: "o", high: "hi", low: "lo", close: "c")""",
        "BoxPlot" to """BoxPlot([{g: "A", v: 3}, {g: "A", v: 5}, {g: "B", v: 4}, {g: "B", v: 8}], x: "g", y: "v")""",
        "Heatmap" to """Heatmap([{day: "Mon", hour: "9", n: 3}, {day: "Tue", hour: "9", n: 5}], x: "day", y: "hour", value: "n")""",
        "Waterfall" to """Waterfall([{item: "Income", amt: 50000}, {item: "Rent", amt: -18000}, {item: "Food", amt: -9000}], x: "item", y: "amt")""",
        // canvas and visual
        "Canvas" to """Canvas(view: [0, 0, 100, 60], label: "Sun over hills") [rect(0, 40, 100, 20, fill: tertiaryContainer), circle(70, 20, 8, fill: primary)]""",
        "Gradient" to """Gradient(h: m) [Title("Good morning")]""",
        "Shader" to """Shader(aurora, h: m) [Title("Welcome")]""",
        "Shape" to """Shape(cookie, size: l, icon: "favorite")""",
        "Blob" to """Blob("asha", tone: tertiary)""",
        "Skeleton" to """Skeleton(kind: list)""",
        // learning and play
        "Quiz" to "\$answers = []\n" + """Quiz(${'$'}answers, [{q: "2 + 2?", options: ["3", "4", "5"], answer: 1, why: "Two pairs make four."}])""",
        "Flashcards" to "\$progress = {}\n" + """Flashcards(${'$'}progress, [{front: "Hola", back: "Hello"}, {front: "Gracias", back: "Thank you"}])""",
        "Blanks" to "\$filled = []\n" + """Blanks(${'$'}filled, "Water boils at [[100]] degrees Celsius.")""",
        "Match" to "\$pairs = []\n" + """Match(${'$'}pairs, ["Paris", "Rome"], ["Italy", "France"])""",
        "Board" to "\$cells = [[\"\", \"\", \"\"], [\"\", \"\", \"\"], [\"\", \"\", \"\"]]\n" +
            """Board(${'$'}cells, 3, 3, render: (cell, r, c) => Text(cell, style: headlineMedium, align: center), onCell: (r, c) => !set(${'$'}cells[r][c], "X"))""",
        "Dice" to "\$roll = [1, 1]\n" + """Dice(${'$'}roll, count: 2)""",
        "Spinner" to "\$pick = \"\"\n" + """Spinner(${'$'}pick, ["Pizza", "Thali", "Sushi"])""",
        "Scratch" to """Scratch [Title("You won a coffee!")]""",
    )

    val marks: Map<String, String> = mapOf(
        "Chart.line" to """line(x: "month", y: "total", tone: primary)""",
        "Chart.area" to """area(x: "month", y: "total", tone: tertiary)""",
        "Chart.bar" to """bar(x: "month", y: "total", labels: true)""",
        "Chart.point" to """point(x: "month", y: "total", size: 8)""",
        "Chart.rule" to """rule(y: 100, label: "Target")""",
        "Chart.band" to """band(from: "Jan", to: "Feb", label: "Sale")""",
        "Chart.text" to """text(x: "month", y: "total", content: "peak")""",
        "Chart.arc" to """arc(value: "total", label: "month")""",
        "Chart.range" to """range(x: "month", lo: "low", hi: "high")""",
        "Chart.cell" to """cell(x: "day", y: "hour", value: "n")""",
        "Chart.link" to """link(from: "a", to: "b", value: "v")""",
        "Canvas.circle" to """circle(50, 30, 8, fill: primary)""",
        "Canvas.rect" to """rect(10, 10, 30, 20, fill: secondaryContainer, radius: 2)""",
        "Canvas.line" to """line(0, 50, 100, 50, stroke: outline)""",
        "Canvas.path" to """path("M 10 50 Q 50 0 90 50", stroke: primary, width: 2)""",
        "Canvas.poly" to """poly([[10, 50], [50, 10], [90, 50]], fill: tertiaryContainer)""",
        "Canvas.label" to """label(50, 55, "Base camp")""",
        "Canvas.icon" to """icon(50, 30, "flag", tone: error)""",
        "Canvas.image" to """image(0, 0, 100, 60, "https://example.com/map.png")""",
        "Canvas.group" to """group(translate: [50, 30], rotate: 45) [rect(-5, -5, 10, 10, fill: primary)]""",
    )

    fun component(name: String): String? = components[name]
    fun mark(container: String, name: String): String? = marks["$container.$name"]
}
