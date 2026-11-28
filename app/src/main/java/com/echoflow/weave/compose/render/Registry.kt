package com.echoflow.weave.compose.render

import androidx.compose.foundation.layout.height

import com.echoflow.weave.compose.canvas.WeaveCanvas
import com.echoflow.weave.compose.chart.Charts
import com.echoflow.weave.compose.chart.ChartsMore
import com.echoflow.weave.compose.components.DataMore
import com.echoflow.weave.compose.components.InputsMore
import com.echoflow.weave.compose.components.Media
import com.echoflow.weave.compose.components.Play
import com.echoflow.weave.compose.components.Visual
import com.echoflow.weave.compose.components.Collections
import com.echoflow.weave.compose.components.Inputs
import com.echoflow.weave.compose.components.Layout
import com.echoflow.weave.compose.components.Navigation
import com.echoflow.weave.compose.components.Numbers
import com.echoflow.weave.compose.components.TextContent

/**
 * Component name → renderer (05 §5.1). The prompt advertises only what is registered here
 * (05 §3.3): `WeaveRuntime.catalog` is filtered to [names].
 */
object Registry {
    val renderers: Map<String, NodeRendererFn> = mapOf(
        // layout
        "Column" to { n, c, m -> Layout.ColumnR(n, c, m) },
        "Row" to { n, c, m -> Layout.RowR(n, c, m) },
        "Box" to { n, c, m -> Layout.BoxR(n, c, m) },
        "Grid" to { n, c, m -> Layout.GridR(n, c, m) },
        "Flow" to { n, c, m -> Layout.FlowR(n, c, m) },
        "Carousel" to { n, c, m -> Layout.CarouselR(n, c, m) },
        "Spacer" to { n, c, m -> Layout.SpacerR(n, c, m) },
        "Divider" to { n, c, m -> Layout.DividerR(n, c, m) },
        "Card" to { n, c, m -> Layout.CardR(n, c, m) },
        "Surface" to { n, c, m -> Layout.SurfaceR(n, c, m) },
        "Section" to { n, c, m -> Layout.SectionR(n, c, m) },
        "Split" to { n, c, m -> Layout.SplitR(n, c, m) },
        "Sticky" to { n, c, m -> Layout.StickyR(n, c, m) },
        // text and content
        "Text" to { n, c, m -> TextContent.TextR(n, c, m) },
        "Title" to { n, c, m -> TextContent.TitleR(n, c, m) },
        "Rich" to { n, c, m -> TextContent.RichR(n, c, m) },
        "Markdown" to { n, c, m -> TextContent.MarkdownR(n, c, m) },
        "Icon" to { n, c, m -> TextContent.IconR(n, c, m) },
        "Avatar" to { n, c, m -> TextContent.AvatarR(n, c, m) },
        "Badge" to { n, c, m -> TextContent.BadgeR(n, c, m) },
        "Chip" to { n, c, m -> TextContent.ChipR(n, c, m) },
        "Link" to { n, c, m -> TextContent.LinkR(n, c, m) },
        "Quote" to { n, c, m -> TextContent.QuoteR(n, c, m) },
        "Callout" to { n, c, m -> TextContent.CalloutR(n, c, m) },
        "Code" to { n, c, m -> TextContent.CodeR(n, c, m) },
        "Kbd" to { n, c, m -> TextContent.KbdR(n, c, m) },
        "Empty" to { n, c, m -> TextContent.EmptyR(n, c, m) },
        // numbers and status
        "Metric" to { n, c, m -> Numbers.MetricR(n, c, m) },
        "Stat" to { n, c, m -> Numbers.StatR(n, c, m) },
        "Progress" to { n, c, m -> Numbers.ProgressR(n, c, m) },
        "Gauge" to { n, c, m -> Numbers.GaugeR(n, c, m) },
        "Rating" to { n, c, m -> Numbers.RatingR(n, c, m) },
        "Steps" to { n, c, m -> Numbers.StepsR(n, c, m) },
        "Timer" to { n, c, m -> Numbers.TimerR(n, c, m) },
        "Clock" to { n, c, m -> Numbers.ClockR(n, c, m) },
        // inputs
        "Button" to { n, c, m -> Inputs.ButtonR(n, c, m) },
        "IconButton" to { n, c, m -> Inputs.IconButtonR(n, c, m) },
        "Toggle" to { n, c, m -> Inputs.ToggleR(n, c, m) },
        "Check" to { n, c, m -> Inputs.CheckR(n, c, m) },
        "Radio" to { n, c, m -> Inputs.RadioR(n, c, m) },
        "Segmented" to { n, c, m -> Inputs.SegmentedR(n, c, m) },
        "Chips" to { n, c, m -> Inputs.ChipsR(n, c, m) },
        "Slider" to { n, c, m -> Inputs.SliderR(n, c, m) },
        "Stepper" to { n, c, m -> Inputs.StepperR(n, c, m) },
        "TextField" to { n, c, m -> Inputs.TextFieldR(n, c, m) },
        "Select" to { n, c, m -> Inputs.SelectR(n, c, m) },
        "Form" to { n, c, m -> Inputs.FormR(n, c, m) },
        // collections and data
        "List" to { n, c, m -> Collections.ListR(n, c, m) },
        "Item" to { n, c, m -> Collections.ItemR(n, c, m) },
        "Table" to { n, c, m -> Collections.TableR(n, c, m) },
        "Tree" to { n, c, m -> Collections.TreeR(n, c, m) },
        // navigation
        "Tabs" to { n, c, m -> Navigation.TabsR(n, c, m) },
        "Pager" to { n, c, m -> Navigation.PagerR(n, c, m) },
        "Wizard" to { n, c, m -> Navigation.WizardR(n, c, m) },
        "Accordion" to { n, c, m -> Navigation.AccordionR(n, c, m) },
        "Reveal" to { n, c, m -> Navigation.RevealR(n, c, m) },
        "Flip" to { n, c, m -> Navigation.FlipR(n, c, m) },
        // charts
        "Chart" to { n, c, m -> Charts.ChartR(n, c, m) },
        "LineChart" to { n, c, m -> Charts.ChartR(n, c, m) },
        "AreaChart" to { n, c, m -> Charts.ChartR(n, c, m) },
        "BarChart" to { n, c, m -> Charts.ChartR(n, c, m) },
        "PieChart" to { n, c, m -> Charts.ChartR(n, c, m) },
        "DonutChart" to { n, c, m -> Charts.ChartR(n, c, m) },
        "ScatterChart" to { n, c, m -> Charts.ChartR(n, c, m) },
        "Histogram" to { n, c, m -> Charts.ChartR(n, c, m) },
        "Waterfall" to { n, c, m -> Charts.ChartR(n, c, m) },
        "Radar" to { n, c, m -> ChartsMore.Render(n, c, m) },
        "Funnel" to { n, c, m -> ChartsMore.Render(n, c, m) },
        "Treemap" to { n, c, m -> ChartsMore.Render(n, c, m) },
        "Sankey" to { n, c, m -> ChartsMore.Render(n, c, m) },
        "Candles" to { n, c, m -> ChartsMore.Render(n, c, m) },
        "BoxPlot" to { n, c, m -> ChartsMore.Render(n, c, m) },
        "Heatmap" to { n, c, m -> ChartsMore.Render(n, c, m) },
        // inputs added in phase 2–3
        "Fab" to { n, c, m -> InputsMore.FabR(n, c, m) },
        "SplitButton" to { n, c, m -> InputsMore.SplitButtonR(n, c, m) },
        "ButtonGroup" to { n, c, m -> InputsMore.ButtonGroupR(n, c, m) },
        "RangeSlider" to { n, c, m -> InputsMore.RangeSliderR(n, c, m) },
        "DatePicker" to { n, c, m -> InputsMore.DatePickerR(n, c, m) },
        "DateRange" to { n, c, m -> InputsMore.DateRangeR(n, c, m) },
        "TimePicker" to { n, c, m -> InputsMore.TimePickerR(n, c, m) },
        "ColorPick" to { n, c, m -> InputsMore.ColorPickR(n, c, m) },
        "RatingInput" to { n, c, m -> InputsMore.RatingInputR(n, c, m) },
        "Pad2D" to { n, c, m -> InputsMore.Pad2DR(n, c, m) },
        "Sketch" to { n, c, m -> InputsMore.SketchR(n, c, m) },
        "Swipe" to { n, c, m -> InputsMore.SwipeR(n, c, m) },
        "Rank" to { n, c, m -> InputsMore.RankR(n, c, m) },
        "Sort" to { n, c, m -> InputsMore.SortR(n, c, m) },
        // data
        "Timeline" to { n, c, m -> DataMore.TimelineR(n, c, m) },
        "Calendar" to { n, c, m -> DataMore.CalendarR(n, c, m) },
        "Heatcal" to { n, c, m -> DataMore.HeatcalR(n, c, m) },
        "Diff" to { n, c, m -> DataMore.DiffR(n, c, m) },
        "Json" to { n, c, m -> DataMore.JsonR(n, c, m) },
        // media
        "Image" to { n, c, m -> Media.ImageR(n, c, m) },
        "Gallery" to { n, c, m -> Media.GalleryR(n, c, m) },
        "Video" to { n, c, m -> Media.VideoR(n, c, m) },
        "Audio" to { n, c, m -> Media.AudioR(n, c, m) },
        "Map" to { n, c, m -> com.echoflow.weave.compose.components.Spatial.MapR(n, c, m) },
        "Model3D" to { n, c, m -> com.echoflow.weave.compose.components.Spatial.Model3DR(n, c, m) },
        "Lottie" to { n, c, m -> Media.LottieR(n, c, m) },
        "Qr" to { n, c, m -> Media.QrR(n, c, m) },
        // overlays render nothing in the flow; the block draws open ones (04 §11)
        "Sheet" to { _, _, _ -> },
        "Dialog" to { _, _, _ -> },
        "Popover" to { _, _, _ -> },
        "Menu" to { _, _, _ -> },
        // canvas and visual
        "Canvas" to { n, c, m -> WeaveCanvas.CanvasR(n, c, m) },
        "Gradient" to { n, c, m -> Visual.GradientR(n, c, m) },
        "Shader" to { n, c, m -> Visual.ShaderR(n, c, m) },
        "Shape" to { n, c, m -> Visual.ShapeR(n, c, m) },
        "Blob" to { n, c, m -> Visual.BlobR(n, c, m) },
        "Skeleton" to { n, c, m -> Visual.SkeletonR(n, c, m) },
        // learning and play
        "Quiz" to { n, c, m -> Play.QuizR(n, c, m) },
        "Flashcards" to { n, c, m -> Play.FlashcardsR(n, c, m) },
        "Blanks" to { n, c, m -> Play.BlanksR(n, c, m) },
        "Match" to { n, c, m -> Play.MatchR(n, c, m) },
        "Board" to { n, c, m -> Play.BoardR(n, c, m) },
        "Dice" to { n, c, m -> Play.DiceR(n, c, m) },
        "Spinner" to { n, c, m -> Play.SpinnerR(n, c, m) },
        "Scratch" to { n, c, m -> Play.ScratchR(n, c, m) },
        // lazy parts (03 §9.3)
        "Lazy" to { n, c, m -> LazyPart.Render(n, c, m) },
        "Sparkline" to { n, c, m ->
            val values = (n.args["values"] as? com.echoflow.weave.core.eval.WList)?.items?.mapNotNull { (it as? com.echoflow.weave.core.eval.WNum)?.v } ?: emptyList()
            val color = com.echoflow.weave.compose.tokens.TokenResolver.tone(n.word("tone") ?: "primary", "high", androidx.compose.material3.MaterialTheme.colorScheme, c.dark)?.container
                ?: androidx.compose.material3.MaterialTheme.colorScheme.primary
            Numbers.Sparkline(values, color, m.height(com.echoflow.weave.compose.tokens.TokenResolver.heightOr(n.args["h"], "xs", c.screenHeight)))
        },
    )

    val names: Set<String> get() = renderers.keys
}

