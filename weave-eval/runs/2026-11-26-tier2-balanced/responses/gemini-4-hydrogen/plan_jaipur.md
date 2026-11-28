Here is a 2-day Jaipur itinerary organised by morning, afternoon, and evening for each day.

```weave
@weave 1 id: jaipur_itinerary title: "2-Day Jaipur itinerary"
$day = 0
root = Card(gap: m) [
  Title("Jaipur in 2 days", subtitle: "Forts, palaces, and Pink City markets", icon: "temple_hindu"),
  Tabs($day, labels: ["Day 1: Forts & Views", "Day 2: Old City & Markets"]) [
    dayView(day1),
    dayView(day2)
  ],
  Button("Customise for my trip", icon: "tune", variant: tonal, onTap: !say("Adjust this 2-day Jaipur itinerary with food recommendations and transport tips"))
]
fn dayView(slots) = List(slots, render: s => Item(s.place, subtitle: s.plan, overline: s.time))
day1 = [
  {time: "Morning · 8:30 AM", place: "Amber Fort & Maota Lake", plan: "Explore Sheesh Mahal early before the crowds; stop at Panna Meena ka Kund nearby."},
  {time: "Afternoon · 1:00 PM", place: "Jal Mahal & Jaigarh Fort", plan: "Lunch near Amer Road, then head up to Jaigarh for panoramic Aravalli views and the Jaivana cannon."},
  {time: "Evening · 5:30 PM", place: "Nahargarh Fort sunset", plan: "Watch sunset over the Pink City from the ramparts, followed by dinner at Chokhi Dhani or a rooftop cafe."}
]
day2 = [
  {time: "Morning · 9:00 AM", place: "Hawa Mahal & City Palace", plan: "Start with Hawa Mahal photos from across the street, then tour City Palace courtyards and Chandra Mahal."},
  {time: "Afternoon · 1:30 PM", place: "Jantar Mantar & Albert Hall", plan: "Visit the UNESCO astronomical instruments next door, have Rajasthani thali, and tour Albert Hall Museum."},
  {time: "Evening · 5:30 PM", place: "Johari & Bapu Bazaar", plan: "Browse block-print textiles, mojris, and Lac bangles; finish with lassi at Lassiwala on MI Road."}
]
on change($day) -> !haptic(tick)
```
