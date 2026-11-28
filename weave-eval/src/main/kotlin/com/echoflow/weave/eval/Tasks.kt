package com.echoflow.weave.eval

import com.echoflow.weave.eval.Check.AnyOf
import com.echoflow.weave.eval.Check.Has
import com.echoflow.weave.eval.Check.Input
import com.echoflow.weave.eval.Check.Interactive
import com.echoflow.weave.eval.Check.Number
import com.echoflow.weave.eval.Check.Text

/** One eval task (07 §10.1). [control] prompts should get plain text under Balanced. */
data class EvalTask(
    val id: String,
    val category: String,
    val tier: Int,
    val prompt: String,
    val checks: List<Check>,
    val control: Boolean = false,
)

/**
 * The 60 tasks in 6 categories plus 20 controls (07 §10.1–10.2). Expected numbers are computed
 * from the formulas in 03 §10 (see the comments); checks read what the block shows after the
 * listed inputs are set.
 */
object Tasks {
    private fun t(id: String, cat: String, prompt: String, vararg checks: Check, tier: Int = 2) =
        EvalTask(id, cat, tier, prompt, checks.toList())

    private val chart = Has(CHARTS)
    private val table = Has(setOf("Table"))

    val calculators = listOf(
        // emi(5000000, 0.085, 20) = 43391.16
        t("calc_emi", "calculators", "Make me an EMI calculator for a ₹50 lakh home loan over 20 years. Let me change the interest rate with a slider, starting at 8%.",
            // The starting rate is part of the task, so it is checked before the slider is moved.
            Number(8.0, "rate|interest", tol = 0.01, relTol = 0.0),
            Input("rate|interest", 8.5), Number(43391.16, "emi|month|instal", tol = 1.0)),
        // 3000 * 1.10 / 5 = 660
        t("calc_tip", "calculators", "Build a tip splitter: the bill is ₹2,400, tip 10%, split between 4 people. Let me change the bill amount and the number of people.",
            Input("bill|amount|total", 3000.0), Input("people|person|split|friends", 5.0), Number(660.0, tol = 0.5)),
        // 42.195 / 1.609344 = 26.2188
        t("calc_km_mi", "calculators", "Make a kilometres to miles converter with a slider for the kilometres.",
            Input("km|kilomet|distance", 42.195), Number(26.2188, tol = 0.01, relTol = 0.0)),
        // 70 / 1.75^2 = 22.857
        t("calc_bmi", "calculators", "Build a BMI calculator with inputs for weight in kg and height in cm.",
            Input("weight|kg", 70.0), Input("height|cm", 175.0), Number(22.857, tol = 0.05, relTol = 0.0)),
        // 100000 * 1.07^10 = 196715.14
        t("calc_compound", "calculators", "Make a compound interest calculator: ₹1,00,000 invested at 7% a year, compounded yearly, with a slider for the number of years from 1 to 30.",
            Input("year|period|duration|time", 10.0), Number(196715.14, tol = 1.0)),
        // 500000 * (1 + 0.065/4)^(4*5) = 690209.89
        t("calc_fd", "calculators", "Fixed deposit calculator: ₹5,00,000 at 6.5% a year compounded quarterly. Add a slider for the number of years and show the maturity amount.",
            Input("year|period|tenure|duration|time", 5.0), Number(690209.89, tol = 1.0)),
        // 37 * 9/5 + 32 = 98.6
        t("calc_temp", "calculators", "Celsius to Fahrenheit converter with a slider for the temperature in Celsius.",
            Input("celsius|°c|\\bc\\b|temp", 37.0), Number(98.6, tol = 0.05, relTol = 0.0)),
        // 4000 * 0.7 = 2800
        t("calc_discount", "calculators", "Shopping discount calculator: the price is ₹2,499 and the discount is 30%. Let me change the price, and show the final price and how much I save.",
            Input("price|mrp|amount|cost", 4000.0), Number(2800.0, tol = 0.5)),
        // 500 / 15 * 105 = 3500
        t("calc_fuel", "calculators", "Trip fuel cost calculator: distance 350 km, mileage 15 km per litre, petrol at ₹105 a litre. Let me change the distance.",
            Input("distance|km", 500.0), Number(3500.0, tol = 0.5)),
        // 2500 * 1.18 = 2950
        t("calc_gst", "calculators", "GST calculator: amount before tax ₹1,000 at 18% GST. Let me change the amount and show the tax and the total.",
            Input("amount|price|base|value", 2500.0), Number(2950.0, tol = 0.5)),
    )

    val data = listOf(
        t("data_expenses", "data", "Here are my monthly expenses: Rent 18000, Food 9000, Travel 4000, Bills 3500, Fun 5500. Show them as a chart and a table with the total.",
            chart, Number(40000.0, tol = 0.5)),
        t("data_delhi_temp", "data", "Plot Delhi's average monthly maximum temperature in °C: Jan 21, Feb 24, Mar 30, Apr 36, May 40, Jun 39, Jul 35, Aug 34, Sep 34, Oct 33, Nov 28, Dec 23. Use a line chart and point out the hottest month.",
            chart, Text("may")),
        t("data_quarters", "data", "Quarterly sales in lakh rupees: Q1 120, Q2 150, Q3 90, Q4 200. Show a bar chart and the yearly total.",
            chart, Number(560.0, tol = 0.5)),
        t("data_population", "data", "Population in millions: India 1441, China 1425, USA 341, Indonesia 279, Pakistan 245. Show a bar chart and a table.",
            chart, table),
        // total 33, average 33/7 = 4.71
        t("data_running", "data", "My running log this week in km: Mon 5, Tue 0, Wed 7, Thu 3, Fri 0, Sat 12, Sun 6. Chart it and show the weekly total and the average per day.",
            chart, Number(33.0, tol = 0.01, relTol = 0.0), Number(4.71, tol = 0.01, relTol = 0.0)),
        // pass = 43/45 = 95.6 %
        t("data_grades", "data", "Grade distribution for my class: A 12, B 18, C 9, D 4, F 2. Show a chart and the pass percentage, where A to D pass.",
            chart, Number(95.56, tol = 0.1, relTol = 0.0)),
        // growth (2600-1200)/1200 = 116.7 %
        t("data_visits", "data", "Website visits for the last 6 months, May to October: 1200, 1500, 1350, 1800, 2100, 2600. Show a chart and the growth from the first month to the last.",
            chart, AnyOf(listOf(Number(116.67, tol = 0.1, relTol = 0.0), Number(1400.0, tol = 0.5)))),
        // 1650 units * 7 = 11550
        t("data_electricity", "data", "Electricity units used each month: Jan 220, Feb 190, Mar 210, Apr 280, May 350, Jun 400. Show a chart and what the total would cost at ₹7 per unit.",
            chart, Number(11550.0, tol = 0.5)),
        t("data_portfolio", "data", "My portfolio of ₹10,00,000 is Equity 60%, Debt 25%, Gold 10%, Cash 5%. Show a chart and a table with the amount in each.",
            chart, Number(600000.0, tol = 0.5)),
        t("data_scores", "data", "Exam scores: Maths 88, Physics 76, Chemistry 81, English 92, Computer Science 95. Show a chart, a table and the average.",
            chart, Number(86.4, tol = 0.05, relTol = 0.0)),
    )

    val planning = listOf(
        t("plan_jaipur", "planning", "Plan a 2-day Jaipur itinerary with morning, afternoon and evening for each day.",
            Text("day\\s*1|day one"), Text("day\\s*2|day two"), Text("amber|hawa|city palace|nahargarh|jantar")),
        t("plan_workout", "planning", "Make a weekly workout plan with 3 strength days, 2 cardio days and 2 rest days.",
            Text("strength"), Text("cardio"), Text("rest")),
        t("plan_packing", "planning", "Packing checklist for a 5-day beach trip that I can tick off as I pack.",
            Text("sunscreen|swim"), Interactive),
        t("plan_study", "planning", "Make a study timetable for exam week: 5 subjects over 5 days, 6 hours a day.",
            Text("day|mon"), AnyOf(listOf(table, Has(setOf("List", "Item", "Tabs"))))),
        // 50/30/20 of 80000: needs = 40000
        t("plan_budget", "planning", "Plan a monthly budget for a ₹60,000 salary using the 50/30/20 rule, with a slider for the salary.",
            Input("salary|income|take", 80000.0), Number(40000.0, tol = 0.5)),
        t("plan_wedding", "planning", "Give me a wedding planning timeline from 6 months before to the day itself, with the key tasks at each point.",
            Text("month"), Text("venue|caterer|invitation|photographer")),
        t("plan_meals", "planning", "Make a vegetarian meal plan for a week with 3 meals a day.",
            Text("breakfast"), Text("lunch"), Text("dinner")),
        t("plan_routine", "planning", "Plan a daily routine for a remote worker from 7am to 10pm with focus blocks and breaks.",
            Text("break"), Text("focus|deep work")),
        // 300000 / 10 = 30000
        t("plan_savings", "planning", "Savings goal planner: I want ₹3,00,000 in 12 months. Add a slider for the number of months and show how much to save each month.",
            Input("month|period|duration|time", 10.0), Number(30000.0, tol = 0.5)),
        t("plan_morning", "planning", "A morning routine checklist with 6 steps and a count of how many I've done.",
            Interactive, Text("done|complete|\\d\\s*/\\s*6|of 6")),
    )

    val learning = listOf(
        t("learn_solar", "learning", "Quiz me with 5 questions about the solar system.", Interactive, Text("planet|sun|orbit")),
        t("learn_spanish", "learning", "Flashcards for 8 common Spanish greetings.", Interactive, Text("hola")),
        t("learn_photo", "learning", "A 3-question quiz on photosynthesis.", Interactive, Text("chlorophyll|chloroplast|oxygen|carbon dioxide|glucose")),
        t("learn_capitals", "learning", "Fill in the blanks: the capitals of 5 Indian states.", Interactive,
            Text("mumbai|chennai|kolkata|bengaluru|bangalore|jaipur|lucknow|patna|bhopal|hyderabad|thiruvananthapuram|gandhinagar|dispur|shimla|raipur|ranchi")),
        t("learn_tables", "learning", "Help me practise multiplication tables, with a slider to pick which table (1 to 12).",
            Input("table|number|times|of", 9.0), Text("81")),
        t("learn_elements", "learning", "Quiz me on the symbols of the first 10 elements of the periodic table.", Interactive, Text("hydrogen|helium|lithium|neon")),
        // simple 10000*(1+0.1*10) = 20000; compound 10000*1.1^10 = 25937.42
        t("learn_interest", "learning", "Explain simple vs compound interest for ₹10,000 at 10% a year, with a slider for the number of years.",
            Input("year|period|time|duration", 10.0), Number(20000.0, tol = 0.5), Number(25937.42, tol = 1.0)),
        t("learn_python", "learning", "Flashcards for 6 common Python list methods.", Interactive, Text("append")),
        t("learn_freedom", "learning", "A 4-question quiz about India's freedom struggle.", Interactive, Text("1947|gandhi|nehru|bose|salt|quit india")),
        // hypot(6, 8) = 10
        t("learn_pythagoras", "learning", "Teach me Pythagoras' theorem with sliders for the base and the height of a right triangle, showing the hypotenuse.",
            Input("base|\\ba\\b|adjacent", 6.0), Input("height|\\bb\\b|perpendicular|opposite", 8.0), Number(10.0, tol = 0.01, relTol = 0.0)),
    )

    val comparison = listOf(
        t("cmp_phones", "comparison", "Compare these phones in a table and a chart: Pixel 9 ₹79,999 with 4700 mAh, Galaxy S25 ₹80,999 with 3900 mAh, iPhone 16 ₹79,900 with 3561 mAh.",
            table, Number(3561.0, tol = 0.5)),
        // emi(4000000, 0.085, 20) = 34712.93; emi(4000000, 0.089, 15) = 40333.06
        t("cmp_loans", "comparison", "Compare two ₹40 lakh home loan offers: Bank A at 8.5% for 20 years and Bank B at 8.9% for 15 years. Show the monthly EMI and total interest of each.",
            // EMI and total interest: total = emi * years * 12 - 40,00,000.
            Number(34712.93, tol = 1.0), Number(40333.06, tol = 1.0),
            Number(4331103.0, relTol = 0.002), Number(3259951.0, relTol = 0.002)),
        // 2999 / 365 = 8.22 per day
        t("cmp_mobile", "comparison", "Compare mobile plans by cost per day: Plan A ₹299 for 28 days, Plan B ₹719 for 84 days, Plan C ₹2,999 for 365 days.",
            Number(8.22, tol = 0.01, relTol = 0.0), Number(10.68, tol = 0.01, relTol = 0.0)),
        // EV 15 kWh/100 km * ₹8 = 1.2/km → 1200 for 1000 km; petrol 105/15 = 7/km → 7000
        t("cmp_ev", "comparison", "Compare running costs of an EV (15 kWh per 100 km, electricity ₹8 per kWh) and a petrol car (15 km per litre, petrol ₹105 a litre), with a slider for kilometres driven per month.",
            Input("km|distance|kilomet|drive", 1000.0), Number(1200.0, tol = 0.5), Number(7000.0, tol = 0.5)),
        // 360000 * sum(1.05^i, i < 10) = 4528041
        t("cmp_rent", "comparison", "Rent vs buy over 10 years: rent is ₹30,000 a month and rises 5% a year; the home loan EMI is ₹55,000 a month, fixed. Show the total paid for each over 10 years.",
            Number(4528041.0, relTol = 0.005), Number(6600000.0, tol = 1.0)),
        t("cmp_laptops", "comparison", "Compare three laptops in a sortable table: Air 13 at 1.24 kg for ₹99,900, Zen 14 at 1.39 kg for ₹84,990, Book 15 at 1.6 kg for ₹69,999.",
            table, Number(1.24, tol = 0.001, relTol = 0.0)),
        // FD 7%: 196715.14; equity 12%: 310584.82 for ₹1,00,000 over 10 years
        t("cmp_fd_equity", "comparison", "Compare ₹1,00,000 in a fixed deposit at 7% vs equity at 12% a year (both compounded yearly), with a slider for years.",
            Input("year|period|time|duration", 10.0), Number(196715.14, tol = 1.0), Number(310584.82, tol = 1.0)),
        // 650 / 2000 = 0.325 (0.317 with 2048 GB); 130 / 100 = 1.3
        t("cmp_storage", "comparison", "Compare cloud storage plans by price per GB per month: 100 GB for ₹130, 200 GB for ₹210, 2 TB for ₹650 a month.",
            Number(1.3, tol = 0.005, relTol = 0.0), Number(0.325, tol = 0.011, relTol = 0.0)),
        t("cmp_cities", "comparison", "Compare monthly costs in Bengaluru, Pune and Hyderabad: rent ₹28,000 / ₹20,000 / ₹18,000, food ₹12,000 / ₹10,000 / ₹10,000, transport ₹4,000 / ₹3,000 / ₹3,500. Show the totals.",
            Number(44000.0, tol = 0.5), Number(33000.0, tol = 0.5), Number(31500.0, tol = 0.5)),
        // card C 1.5% of 40000 = 600; card B 2% capped at 500
        t("cmp_cards", "comparison", "Compare three credit cards with a slider for monthly spend: A gives 1% cashback, B gives 2% capped at ₹500 a month, C gives 1.5%.",
            Input("spend|amount|month", 40000.0), Number(600.0, tol = 0.5), Number(500.0, tol = 0.5)),
    )

    /** Tier 4 only (03 §8, 04 §13): not shipped in phase 1, kept for the phase 3 run. */
    val play = listOf(
        t("play_dice", "play", "A dice roller for two dice with the total.", Interactive, tier = 4),
        t("play_ttt", "play", "A tic-tac-toe game I can play against a friend.", Interactive, tier = 4),
        t("play_bounce", "play", "A bouncing ball simulation with gravity I can change.", Interactive, tier = 4),
        t("play_reaction", "play", "A reaction time game: tap as soon as the colour changes.", Interactive, tier = 4),
        t("play_counter", "play", "A tally counter with haptics and an undo.", Interactive, tier = 4),
        t("play_guess", "play", "A guess-the-number game from 1 to 100 with higher/lower hints.", Interactive, tier = 4),
        t("play_pendulum", "play", "A pendulum simulation with a slider for the length.", Interactive, tier = 4),
        t("play_snake", "play", "A tiny snake game on a 10 by 10 board.", Interactive, tier = 4),
        t("play_memory", "play", "A memory card matching game with 8 pairs.", Interactive, tier = 4),
        t("play_breath", "play", "A breathing exercise animation: inhale 4 s, hold 4 s, exhale 4 s.", Interactive, tier = 4),
    )

    val controls: List<EvalTask> = listOf(
        "What's the capital of Australia?", "Tell me a joke.", "How do I boil an egg?", "What does HTTP stand for?",
        "How do you say 'thank you' in French?", "Who wrote Hamlet?", "Explain recursion in one sentence.", "Good morning!",
        "What's 15 times 12?", "Summarise the plot of Romeo and Juliet in two lines.", "Is coffee bad for you?", "Write a haiku about rain.",
        "What year did World War 2 end?", "How do I reverse a string in Python?", "What's the difference between affect and effect?",
        "Recommend a good science fiction book.", "What is GDP?", "How do you spell 'necessary'?", "Why is the sky blue?", "Thanks, that helped!",
    ).mapIndexed { i, p -> EvalTask("control_%02d".format(i + 1), "control", 2, p, emptyList(), control = true) }

    val all: List<EvalTask> = calculators + data + planning + learning + comparison + play + controls

    /** Tasks that apply at [shippedTier] (phase 1 ships tier 2: play is skipped). */
    fun forTier(shippedTier: Int) = all.filter { it.tier <= shippedTier }
}
