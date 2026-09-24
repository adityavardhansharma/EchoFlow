package com.echoflow.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoflow.ui.theme.Spacing
import java.util.Calendar
import kotlin.random.Random

/**
 * The empty chat: nothing but a greeting for the time of day.
 *
 * No logo, no suggestion pills, no entrance motion — the text is simply there on the first
 * frame, so opening the app never feels like waiting. The only colour is the [Greeting.accent]
 * phrase in `primary`, which lets each palette put its own mark on an otherwise all-type screen.
 */
@Composable
internal fun EmptyState(topInset: Dp, bottomInset: Dp) {
    // Picked when the empty state appears (app open, new chat) and kept across rotation.
    val greeting = rememberSaveable(saver = GreetingSaver) {
        ChatGreetings.next(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
    }
    val accentColor = MaterialTheme.colorScheme.primary
    val headline = remember(greeting, accentColor) {
        buildAnnotatedString {
            append(greeting.headline)
            val accent = greeting.accent ?: return@buildAnnotatedString
            val start = greeting.headline.indexOf(accent)
            if (start >= 0) addStyle(SpanStyle(color = accentColor), start, start + accent.length)
        }
    }

    // Centre in the space between the top bar and the composer (which grows with the keyboard),
    // lifted a touch: dead-centre text above a heavy input bar reads as sinking.
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(top = topInset, bottom = bottomInset + OpticalLift)
            .padding(horizontal = Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        // Landscape and keyboard-up leave little room; the headline alone carries it there.
        val showSubline = maxHeight >= 160.dp
        Column(
            Modifier.widthIn(max = 320.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                headline,
                style = MaterialTheme.typography.displaySmall.copy(
                    letterSpacing = (-0.25).sp,
                    lineBreak = LineBreak.Heading,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            if (showSubline) {
                Text(
                    greeting.subline,
                    style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Heading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Twice the upward nudge: the extra bottom padding raises the centre by half of it. */
private val OpticalLift = 48.dp

private val GreetingSaver = listSaver<Greeting, String>(
    save = { listOf(it.headline, it.subline, it.accent.orEmpty()) },
    restore = { Greeting(it[0], it[1], it[2].ifEmpty { null }) },
)

/** A headline and its quieter subline. [accent] is the phrase in [headline] painted in `primary`. */
internal data class Greeting(val headline: String, val subline: String, val accent: String? = null)

internal enum class GreetingTime {
    LateNight, EarlyMorning, Morning, Afternoon, Evening, Night;

    companion object {
        fun of(hour: Int): GreetingTime = when (hour) {
            in 0..4 -> LateNight
            in 5..7 -> EarlyMorning
            in 8..11 -> Morning
            in 12..16 -> Afternoon
            in 17..20 -> Evening
            else -> Night
        }
    }
}

/**
 * Twenty greetings per time of day. House style: headline short enough to sit on one or two
 * balanced lines, subline a calm invitation, no exclamation marks, no emoji, never cutesy.
 */
internal object ChatGreetings {
    private var lastShown: Greeting? = null

    /** A fresh greeting for [hour], never the one shown last in this process. */
    fun next(hour: Int): Greeting = pick(hour, lastShown).also { lastShown = it }

    fun pick(hour: Int, previous: Greeting?, random: Random = Random.Default): Greeting =
        forTime(GreetingTime.of(hour)).filter { it != previous }.random(random)

    fun forTime(time: GreetingTime): List<Greeting> = when (time) {
        GreetingTime.LateNight -> lateNight
        GreetingTime.EarlyMorning -> earlyMorning
        GreetingTime.Morning -> morning
        GreetingTime.Afternoon -> afternoon
        GreetingTime.Evening -> evening
        GreetingTime.Night -> night
    }

    // 00:00–04:59
    private val lateNight = listOf(
        Greeting("Hey, night owl", "Let's talk", "night owl"),
        Greeting("Still up?", "Me too"),
        Greeting("Burning the midnight oil", "Let's make it count", "midnight oil"),
        Greeting("The quiet hours", "Best time to think", "quiet hours"),
        Greeting("Can't sleep?", "Let's talk it through"),
        Greeting("Up past midnight", "What's keeping you awake?", "midnight"),
        Greeting("Small hours", "Big thoughts?", "Small hours"),
        Greeting("The world's asleep", "Let's think out loud"),
        Greeting("Moonlight shift", "Let's get into it", "Moonlight"),
        Greeting("Late-night mode", "Let's keep it quiet", "Late-night"),
        Greeting("After hours", "What's on your mind?", "After hours"),
        Greeting("Night shift", "Let's work", "Night shift"),
        Greeting("It's late", "But ideas don't keep hours"),
        Greeting("Wide awake?", "Let's put it to use"),
        Greeting("Hey, stargazer", "Let's look up", "stargazer"),
        Greeting("Night thoughts?", "Let's untangle them", "Night"),
        Greeting("All quiet out there", "Let's make some noise"),
        Greeting("Still going?", "Let's see it through"),
        Greeting("Hello, dreamer", "Let's wander a little", "dreamer"),
        Greeting("Owl hours", "Let's dig in", "Owl hours"),
    )

    // 05:00–07:59
    private val earlyMorning = listOf(
        Greeting("Early start", "Let's plan the day", "Early"),
        Greeting("Up with the sun", "Let's get ahead", "sun"),
        Greeting("Hey, early bird", "Let's get going", "early bird"),
        Greeting("Good morning", "Before the rush", "morning"),
        Greeting("First light", "Clear head, clean slate", "First light"),
        Greeting("Rise and shine", "Let's ease into it", "shine"),
        Greeting("Coffee's brewing", "Let's think while it does"),
        Greeting("Dawn patrol", "Let's get a head start", "Dawn"),
        Greeting("Quiet morning", "Let's use it well", "morning"),
        Greeting("Morning, early riser", "Let's make the most of it", "early riser"),
        Greeting("Sunrise session", "What are we starting?", "Sunrise"),
        Greeting("Before the world wakes", "Let's get ahead of it"),
        Greeting("Fresh out of bed?", "Let's warm up slowly"),
        Greeting("An early one", "Let's make it worth it", "early"),
        Greeting("Hello, sunrise", "Let's begin", "sunrise"),
        Greeting("The day's still new", "Let's shape it"),
        Greeting("Early hours", "Big plans?", "Early hours"),
        Greeting("Morning already?", "Let's get moving", "Morning"),
        Greeting("Up and at it", "Let's talk plans"),
        Greeting("First coffee?", "Let's talk over it"),
    )

    // 08:00–11:59
    private val morning = listOf(
        Greeting("Good morning", "Let's get to work", "morning"),
        Greeting("Morning", "Fresh page, fresh ideas", "Morning"),
        Greeting("Hello, sunshine", "What's on today?", "sunshine"),
        Greeting("Ready when you are", "Let's build something"),
        Greeting("New day", "Let's make it a good one", "New day"),
        Greeting("Morning momentum", "Let's keep it rolling", "Morning"),
        Greeting("Top of the morning", "Let's dive in", "morning"),
        Greeting("Let's get started", "What's first?"),
        Greeting("Morning, ready?", "Let's hit the ground running", "Morning"),
        Greeting("The day is yours", "Where do we start?"),
        Greeting("Clear morning", "Clear thinking", "morning"),
        Greeting("Hello again", "What are we working on?"),
        Greeting("Coffee in hand?", "Let's think big"),
        Greeting("Morning, maker", "Let's create something", "Morning"),
        Greeting("A fresh start", "Let's talk ideas"),
        Greeting("Plans for today?", "Let's line them up"),
        Greeting("Good morning to you", "Let's get into it", "morning"),
        Greeting("Mid-morning check-in", "How's it going so far?", "Mid-morning"),
        Greeting("Morning, thinker", "Let's sharpen some ideas", "Morning"),
        Greeting("Today's a blank page", "Let's fill it in"),
    )

    // 12:00–16:59
    private val afternoon = listOf(
        Greeting("Good afternoon", "Let's keep going", "afternoon"),
        Greeting("Midday already", "Let's keep the pace", "Midday"),
        Greeting("Afternoon", "Let's pick up the thread", "Afternoon"),
        Greeting("Post-lunch focus", "Let's ease back in", "Post-lunch"),
        Greeting("Halfway there", "Let's finish strong", "Halfway"),
        Greeting("Afternoon slump?", "Let's shake it off", "Afternoon"),
        Greeting("Back at it", "Where were we?"),
        Greeting("Good to see you", "Let's work"),
        Greeting("The afternoon stretch", "Let's make headway", "afternoon"),
        Greeting("Second wind", "Let's use it", "Second wind"),
        Greeting("Deep-work hours", "Let's focus", "Deep-work"),
        Greeting("Midday check-in", "What's still on the list?", "Midday"),
        Greeting("Let's keep momentum", "What's next?"),
        Greeting("Hey, you're back", "Let's pick up where we left off"),
        Greeting("Productive afternoon?", "Let's make it one", "afternoon"),
        Greeting("Afternoon light", "Let's think clearly", "Afternoon"),
        Greeting("Still going strong", "Let's keep it up"),
        Greeting("Onward", "Let's work through it"),
        Greeting("Afternoon, friend", "What can we untangle?", "Afternoon"),
        Greeting("In the thick of it?", "Let's lighten the load"),
    )

    // 17:00–20:59
    private val evening = listOf(
        Greeting("Good evening", "Let's think it through", "evening"),
        Greeting("Evening", "How was your day?", "Evening"),
        Greeting("Winding down?", "Let's talk"),
        Greeting("Golden hour", "Let's reflect a little", "Golden hour"),
        Greeting("Evening, friend", "Let's catch up", "Evening"),
        Greeting("Day's almost done", "Let's tie up loose ends"),
        Greeting("After-work hours", "Let's unwind with ideas", "After-work"),
        Greeting("Sunset thoughts?", "Let's hear them", "Sunset"),
        Greeting("Evening session", "Let's work, calmly", "Evening"),
        Greeting("Hello, evening", "Let's slow things down", "evening"),
        Greeting("Dinner can wait", "Let's finish this thought"),
        Greeting("The day's winding down", "Let's make sense of it"),
        Greeting("Twilight hours", "Let's get creative", "Twilight"),
        Greeting("Evening calm", "Let's take our time", "Evening"),
        Greeting("Long day?", "Let's make the rest easy"),
        Greeting("Back for the evening", "What are we exploring?", "evening"),
        Greeting("Lights low", "Ideas bright"),
        Greeting("One more thing?", "Let's get it done"),
        Greeting("Evening, thinker", "Let's wander somewhere new", "Evening"),
        Greeting("As the day settles", "Let's settle a question"),
    )

    // 21:00–23:59
    private val night = listOf(
        Greeting("Late thoughts?", "I'm listening", "Late"),
        Greeting("Hey, night thinker", "Let's wind down", "night thinker"),
        Greeting("Nightcap?", "Let's make it a thought", "Nightcap"),
        Greeting("Evening's end", "Let's wrap up the day", "Evening's end"),
        Greeting("Before bed", "One last idea?", "Before bed"),
        Greeting("Quiet night", "Let's talk softly", "night"),
        Greeting("Tonight", "Let's quiet the noise", "Tonight"),
        Greeting("Night mode", "Let's keep it calm", "Night"),
        Greeting("Still thinking?", "Let's think together"),
        Greeting("Under the stars", "Let's reflect", "stars"),
        Greeting("End of the day", "Let's put it to rest"),
        Greeting("Late but lively?", "Let's run with it", "Late"),
        Greeting("Cozy hours", "Let's chat", "Cozy"),
        Greeting("Hey, night person", "Let's work", "night person"),
        Greeting("Unwinding?", "Let's talk it out"),
        Greeting("Tomorrow's plans?", "Let's sketch them now"),
        Greeting("The night is young", "Let's make something", "night"),
        Greeting("Pajamas on?", "Let's keep it easy"),
        Greeting("Good night-ish", "One more question?", "night"),
        Greeting("Last call for ideas", "Let's hear it", "Last call"),
    )
}
