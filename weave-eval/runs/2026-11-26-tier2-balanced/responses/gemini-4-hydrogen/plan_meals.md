Here is a balanced 7-day vegetarian meal plan with breakfast, lunch, and dinner for each day.

```weave
@weave 1 id: veg_meal_plan title: "Weekly vegetarian meal plan"
$day = 0
root = Card(gap: m) [
  Title("Weekly vegetarian meal plan", subtitle: "21 balanced meals across 7 days", icon: "restaurant_menu"),
  Tabs($day, labels: ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"], scrollable: auto) [
    mealDay("Oats with banana, chia seeds & almonds", "Rajma chawal with cucumber raita", "Palak paneer, 2 phulkas & green salad"),
    mealDay("Vegetable poha with peanuts & lemon", "Chana masala, jeera rice & kachumber", "Moong dal khichdi with roasted papad & curd"),
    mealDay("Moong dal chilla with mint chutney", "Dal tadka, bhindi masala & 2 rotis", "Vegetable pulao with boondi raita"),
    mealDay("Idli sambar with coconut chutney", "Kadai paneer, brown rice & sautéed greens", "Mixed veg soup with whole-wheat garlic toast"),
    mealDay("Besan toast with avocado & cherry tomatoes", "Chole, whole-wheat kulcha & onion salad", "Lauki chana dal, 2 phulkas & curd"),
    mealDay("Aloo paratha (light ghee) with curd", "Lemon rice, curd rice & roasted veggies", "Tofu & broccoli stir-fry with soba noodles"),
    mealDay("Masala dosa with sambar & chutney", "Paneer tikka bowl with quinoa & greens", "Light dalia porridge with roasted veggies")
  ],
  Button("Make grocery list", icon: "shopping_cart", variant: tonal, onTap: !say("Create a categorised grocery list for this 7-day vegetarian meal plan"))
]
fn mealDay(b, l, d) = Column(gap: s) [
  Item(b, overline: "Breakfast"),
  Item(l, overline: "Lunch"),
  Item(d, overline: "Dinner")
]
on change($day) -> !haptic(tick)
```
