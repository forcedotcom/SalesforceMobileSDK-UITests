package com.salesforce.util

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.output.HelpFormatter.ParameterHelp
import com.github.ajalt.clikt.output.MordantMarkdownHelpFormatter
import com.github.ajalt.mordant.animation.progress.ThreadProgressTaskAnimator
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Padding
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.Text
import com.github.ajalt.mordant.widgets.definitionList
import com.github.ajalt.mordant.widgets.progress.MultiProgressBarWidgetMaker
import com.github.ajalt.mordant.widgets.progress.ProgressBarMakerRow
import com.github.ajalt.mordant.widgets.progress.ProgressBarWidgetMaker
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.windowed

const val MAX_OUTPUT_WIDTH = 240

var progressBanner: ThreadProgressTaskAnimator<ProgressState>? = null

var verbosePrinter: Printer? = null

fun ThreadProgressTaskAnimator<ProgressState>.finish() {
    Thread.sleep(500)
    this.stop()
}
data class ProgressState(
    val completedSteps: List<String> = emptyList(),
    val currentStep: String = "",
    val testPassed: Boolean = false,
    val error: String? = null,
) {
    fun advance(nextStep: String) = ProgressState(
        completedSteps = completedSteps + currentStep,
        currentStep = nextStep,
    )

    fun fail(message: String) = copy(error = message)

    fun pass() = copy(testPassed = true)
}

class Printer(private val terminal: Terminal) {
    operator fun invoke(message: String, err: Boolean = false) {
        val widget = if (err) {
            Panel(Text(TextColors.red(message)), padding = Padding(1),)
        } else {
            Panel(Text(TextStyles.bold(message)), padding = Padding(1),)
        }
        terminal.println(widget, stderr = err)
    }

    fun success(message: String) = terminal.println(
        widget = Panel(Text(TextColors.green(message)), padding = Padding(1))
    )
}

object PanelProgressBarMaker : ProgressBarWidgetMaker {
    var title = ""
    override fun build(rows: List<ProgressBarMakerRow<*>>): Widget {
        val inner = definitionList {
            inline = true
            val widgets = MultiProgressBarWidgetMaker.buildCells(rows)
            for ((term, desc) in widgets.flatten().windowed(2, 2)) {
                entry(term, desc)
            }
        }
        return Panel(
            content = inner,
            title = Text(title),
            expand = true,
            padding = Padding( 1, 2, 1, 2),
        )
    }
}

class ArgumentsFirstHelpFormatter(context: Context) :
    MordantMarkdownHelpFormatter(context, showDefaultValues = true) {

    override fun collectParameterSections(parameters: List<ParameterHelp>):
            List<RenderedSection<Widget>> = buildList {

        addAll(renderArguments(parameters))
        addAll(renderOptions(parameters))
        addAll(renderCommands(parameters))
    }

    override fun renderUsageParametersString(parameters: List<ParameterHelp>): String {
        return buildList {
            parameters.filterIsInstance<ParameterHelp.Argument>().mapTo(this) {
                var name = normalizeParameter(it.name)
                if (!it.required) name = renderOptionalMetavar(name)
                if (it.repeatable) name = renderRepeatedMetavar(name)
                if (it.required) styleRequiredUsageParameter(name)
                else styleOptionalUsageParameter(name)
            }

            if (parameters.any { it is ParameterHelp.Option }) {
                val metavar = normalizeParameter(localization.optionsMetavar())
                add(styleOptionalUsageParameter(renderOptionalMetavar(metavar)))
            }

            if (parameters.any { it is ParameterHelp.Subcommand }) {
                val commandMetavar = normalizeParameter(localization.commandMetavar())
                val argsMetavar = normalizeParameter(localization.argumentsMetavar())
                val repeatedArgs = renderRepeatedMetavar(renderOptionalMetavar(argsMetavar))
                add(styleOptionalUsageParameter("$commandMetavar $repeatedArgs"))
            }
        }.joinToString(" ")
    }
}

// Note: this will return null for ./gradlew :TestOrchestrator:run
fun detectTerminalWidth(): Int? {
    return try {
        val process = ProcessBuilder("stty", "size")
        .redirectInput(File("/dev/tty"))
        .redirectErrorStream(true)
        .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        output.split(" ").lastOrNull()?.toIntOrNull()?.let {
            minOf(it, MAX_OUTPUT_WIDTH)
        }
    } catch (_: Exception) {
        null
    }
}