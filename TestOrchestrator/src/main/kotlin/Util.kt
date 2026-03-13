package com.salesforce

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.output.HelpFormatter.ParameterHelp
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.mordant.rendering.Widget
import java.io.File
import java.lang.ProcessBuilder.Redirect.INHERIT

var utilVerboseOutput = false

fun String.runCommand(workingDir: String = ".", verbose: Boolean = utilVerboseOutput): Int =
    split(" ").runCommand(workingDir, verbose)

fun List<String>.runCommand(workingDir: String = ".", verbose: Boolean = utilVerboseOutput): Int {
    val command = if (this.first().contains("xcodebuild")) {
        listOf("/bin/bash", "-c", "set -o pipefail && ${joinToString(" ")} | xcbeautify")
    } else {
        this
    }

    val process = ProcessBuilder(command)
        .directory(File(workingDir))
        .apply {
            if (verbose) {
                redirectOutput(INHERIT)
                redirectError(INHERIT)
            } else {
                redirectErrorStream(true)
            }
        }
        .start()

    val capturedOutput = if (!verbose) process.inputStream.bufferedReader().readText() else null
    val exitCode = process.waitFor()

    if (!verbose && exitCode != 0 && !capturedOutput.isNullOrBlank()) {
        System.err.print(capturedOutput)
    }

    return exitCode
}

class ArgumentsFirstHelpFormatter(context: Context) :
    MordantHelpFormatter(context, showDefaultValues = true) {
    override fun collectParameterSections(parameters: List<ParameterHelp>): List<RenderedSection<Widget>> = buildList {
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