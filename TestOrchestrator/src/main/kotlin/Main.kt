package com.salesforce

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.installMordantMarkdown
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.unique
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
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
import com.salesforce.util.progress
import com.salesforce.util.verboseCommandOutput
import com.salesforce.util.verbosePrinter
import java.io.File
import java.lang.Thread.sleep
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.system.exitProcess

const val DEFAULT_IOS_VERSION = "26.3"
const val DEFAULT_IOS_DEVICE = "iPhone-SE-3rd-generation"

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
    val iOSDevice: String? by option("--device", "--iOSDevice")
        .help("iOS Simulator device type (ex: $DEFAULT_IOS_DEVICE). Uses SimDeviceType identifier format.")
    val appSources: Set<AppSource> by argument("app type or template")
        .help("App type (${AppType.entries.joinToString(", ") { it.name.lowercase() }}) " +
                "\u0085or a template URL/name (CamelCase or URL)" +
                "\u0085or a complex hybrid sample name (e.g. accounteditor)" +
                "\u0085(multiple allowed, space separated)")
        .convert { input ->
            val appType = AppType.entries.find {
                it.name.equals(input, ignoreCase = true) || it.scriptValue.equals(input, ignoreCase = true)
            }
            when {
                appType != null -> AppSource.ByType(os, type = appType)
                else -> AppSource.ByTemplate(os, template = input)
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


    override fun run() {
        val failures = mutableListOf<Pair<String, Exception>>()
        verboseCommandOutput = verboseOutput || IS_CI

        when (os) {
            OS.ANDROID -> {
                if (iOSVersion != null) {
                    throw UsageError("--iOSVersion can only be used with iOS")
                }
                if (iOSDevice != null) {
                    throw UsageError("--device can only be used with iOS")
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
                var totalSteps: Long = when(os) {
                    OS.ANDROID -> 7
                    OS.IOS -> 9
                }
                if (useFirebase) {
                    totalSteps--
                }
                if (appSource.isComplexHybrid) {
                    totalSteps++
                }

                val progressBanner = progressBarContextLayout<ProgressState> {
                    text {
                        context.completedSteps.joinToString("\n") { "${TextColors.green("✔")} $it" }
                    }; text("")
                    text {
                        when {
                            context.testPassed -> {
                                "${TextColors.green("✔")} ${context.currentStep}"
                            }
                            context.error != null -> {
                                "${TextColors.red("✗")} ${context.currentStep}"
                            }
                            else -> {
                                "${TextColors.yellow("➢")} ${context.currentStep}"
                            }
                        }
                    }; spinner(Spinner.Lines())

                    text("\n ");text("\n ")

                    text("Time Elapsed"); timeElapsed()
                    text("Progress"); progressBar()
                    text { if (context.testPassed) "\nCompleted:" else "" };
                    text { if (context.testPassed) TextColors.green("\nLogin Test Passed!") else "" };
                    text { if (context.error != null) "\nError:" else "" };
                    text { if (context.error != null) TextColors.red("\n${context.error!!}") else "" }
                }.animateOnThread(
                    terminal,
                    context = ProgressState(currentStep = "Generate App"),
                    total = totalSteps,
                    maker = marker,
                )
                progressBanner.execute()

                progress = progressBanner
            }

            try {
                val appInfo = if (!reRunTest) {
                    generateApp(appSource, useSF)
                } else {
                    verbosePrinter?.invoke("Skipping App Generation")
                    getAppInfo(appSource)
                }

                compileApp(appInfo, debug)

                runTests(appInfo, iOSVersion ?: DEFAULT_IOS_VERSION, iOSDevice ?: DEFAULT_IOS_DEVICE, useFirebase)
            } catch (e: Exception) {
                failures.add(appSource.appName to e)
                progress?.update {
                    context = context.fail(e.message ?: e.toString())
                }
                sleep(200)
                progress?.stop()
            }
        }

        if (failures.isNotEmpty()) {
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
        const val IOS_TEST_DIR = "./iOS/"
        const val SIM_NAME = "testsim"
        val GCLOUD_RESULTS_DIR: String? by lazy { System.getenv("GCLOUD_RESULTS_DIR") }

        val IS_CI: Boolean by lazy { !System.getenv("GITHUB_WORKFLOW").isNullOrBlank() }
    }
}

fun main(args: Array<String>) = Test().main(args)