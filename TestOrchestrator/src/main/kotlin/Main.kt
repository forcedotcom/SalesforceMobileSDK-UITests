package com.salesforce

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.installMordantMarkdown
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.unique
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.mordant.animation.progress.animateOnThread
import com.github.ajalt.mordant.animation.progress.execute
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Spinner
import com.github.ajalt.mordant.widgets.progress.progressBar
import com.github.ajalt.mordant.widgets.progress.progressBarContextLayout
import com.github.ajalt.mordant.widgets.progress.spinner
import com.github.ajalt.mordant.widgets.progress.text
import com.github.ajalt.mordant.widgets.progress.timeElapsed
import com.salesforce.util.ArgumentsFirstHelpFormatter
import com.salesforce.util.PanelProgressBarMaker
import com.salesforce.util.Printer
import com.salesforce.util.ProgressState
import com.salesforce.util.verbosePrinter
import com.salesforce.util.progress
import com.salesforce.util.verboseCommandOutput
import java.io.File
import kotlin.io.path.Path
import kotlin.system.exitProcess
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString

const val DEFAULT_IOS_VERSION = "26.2"

class Test : CliktCommand() {

    init {
        installMordantMarkdown()
        configureContext {
            terminal = Terminal(width = 240, interactive = true)
            helpFormatter = { ArgumentsFirstHelpFormatter(it) }
        }
    }

    val os: OS by argument().enum<OS>(ignoreCase = true, key = { it.name.lowercase() })
        .help("android or ios")
    val iOSVersion: String? by option("--ios", "--iOSVersion")
        .help("iOS version number (ex: $DEFAULT_IOS_VERSION).")
    val appSources: Set<AppSource> by argument("app type or template")
        .help("An app type (${AppType.entries.joinToString(", ") { it.name.lowercase() }}) " +
                "\u0085or a template URL/name")
        .convert { input ->
            val appType = AppType.entries.find { it.name.equals(input, ignoreCase = true) }
            if (appType != null) {
                AppSource.ByType(os, type = appType)
            } else {
                AppSource.ByTemplate(os, template = input)
            }
        }
        .multiple().unique()
    val useSF: Boolean by option("--sf", "--sfdx").flag()
        .help("Use SF (formerly SFDX) to generate the app.")
    val debug: Boolean by option("-d", "--compileDebug").flag()
        .help("Compile a debug build.")
    val reRunTest: Boolean by option("-r", "--reRun").flag()
        .help("Run the validation test again without re-generating the app.")
    val useFirebase: Boolean by option("-f", "--firebase").boolean().default(IS_CI)
        .help("Run (Android) tests in Firebase Test Lab. Defaults to on for CI and off otherwise.")
    val verboseOutput: Boolean by option("-v", "--verbose").flag()
        .help("Show all command output. Automatically on for CI.")
    val preserverGeneratedApps: Boolean by option("-p", "--preserverGeneratedApps").flag()
        .help("Do not cleanup generated apps from previous runs.")
    // TODO: complexHybrid

    override fun run() {
        val failures = mutableListOf<Pair<String, Exception>>()
        verboseCommandOutput = verboseOutput || IS_CI

        when (os) {
            OS.ANDROID -> {
                if (iOSVersion != null) {
                    throw UsageError("--iOSVersion can only be used with iOS")
                }
                if (appSources.find { it.appName.contains("swift") } != null) {
                    throw UsageError("Swift apps can only be built with iOS")
                }
            }

            OS.IOS -> {
                if (appSources.find { it.appName.contains("kotlin") } != null) {
                    throw UsageError("Kotlin apps can only be built with Android")
                }
            }
        }

        if (!preserverGeneratedApps && !reRunTest) {
            // Remove previous generations
            File(".").listFiles { files ->
                files.isDirectory && files.name.startsWith("tmp")
            }?.forEach { it.deleteRecursively() }
        }

        appSources.forEach { appSource ->
            if (verboseOutput || IS_CI) {
                verbosePrinter = Printer(terminal)
            } else {
                val marker = PanelProgressBarMaker
                marker.title = "Testing ${appSource.appName}"
                val progressBanner = progressBarContextLayout<ProgressState> {
                    text {
                        context.completedSteps.joinToString("\n") { "${TextColors.green("✔")} $it" }
                    }; text("")
                    text { "${TextColors.yellow("⟳")} ${context.currentStep}" }; spinner(Spinner.Lines())
                    text("Time Elapsed"); timeElapsed()
                    text("Progress"); progressBar()
                }.animateOnThread(
                    terminal,
                    context = ProgressState(currentStep = "Generate App"),
                    total = 7,
                    maker = marker,
                )
                progressBanner.execute()

                progress = progressBanner
            }

            try {
                val appInfo = if (!reRunTest) {
                    generateApp(appSource, useSF)
                } else {
                    verbosePrinter?.invoke("Skipping App Generation...")
                    getAppInfo(appSource)
                }
                compileApp(appInfo, debug)

                runTests(appInfo, iOSVersion ?: DEFAULT_IOS_VERSION, useFirebase)
            } catch (e: Exception) {
                progress?.stop()
                terminal.println()
                terminal.println(TextColors.red(e.stackTraceToString()), stderr = true)
                failures.add(appSource.appName to e)
            }
        }

        if (failures.isNotEmpty()) {
            terminal.println(TextColors.red("\n${failures.size} app(s) failed:"), stderr = true)
            failures.forEach { (name, e) ->
                terminal.println(TextColors.red("  - $name: ${e.message}"), stderr = true)
            }
            exitProcess(1)
        }
    }

    companion object {
        val ANDROID_HOME_DIR: String by lazy {
            System.getenv("ANDROID_HOME")
        }
        val ANDROID_BUILD_DIR by lazy {
            Path("$ANDROID_HOME_DIR/build-tools/").listDirectoryEntries().last().pathString
        }
        val ADB by lazy { "$ANDROID_HOME_DIR/platform-tools/adb" }
        const val ANDROID_TEST_DIR = "./Android/"
        val GCLOUD_RESULTS_DIR: String? by lazy { System.getenv("GCLOUD_RESULTS_DIR") }

        val IS_CI: Boolean by lazy { !System.getenv("GITHUB_WORKFLOW").isNullOrBlank() }
    }
}

fun main(args: Array<String>) = Test().main(args)