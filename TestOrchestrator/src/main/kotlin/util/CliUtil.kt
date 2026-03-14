package com.salesforce.util

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.output.HelpFormatter.ParameterHelp
import com.github.ajalt.clikt.output.MordantMarkdownHelpFormatter
import com.github.ajalt.mordant.animation.progress.ThreadProgressTaskAnimator
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.Text
import com.github.ajalt.mordant.widgets.definitionList
import com.github.ajalt.mordant.widgets.progress.MultiProgressBarWidgetMaker
import com.github.ajalt.mordant.widgets.progress.ProgressBarMakerRow
import com.github.ajalt.mordant.widgets.progress.ProgressBarWidgetMaker
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.windowed

var progress: ThreadProgressTaskAnimator<ProgressState>? = null

var verbosePrinter: Printer? = null
data class ProgressState(
    val completedSteps: List<String> = emptyList(),
    val currentStep: String = "",
) {
    fun advance(nextStep: String) = ProgressState(
        completedSteps = completedSteps + currentStep,
        currentStep = nextStep,
    )
}

class Printer(private val terminal: Terminal) {
    operator fun invoke(message: String, err: Boolean = false) {
        if (err) {
            terminal.println(TextColors.red("\n\n$message\n\n"), stderr = true)
        } else {
            terminal.println(TextStyles.bold("\n\n$message\n\n"))
        }
    }

    fun success(message: String) = terminal.println(TextColors.green("\n\n$message\n\n"))
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
        return Panel(inner, title = Text(title))
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