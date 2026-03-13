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
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.mordant.terminal.Terminal
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString

const val DEFAULT_IOS_VERSION = "26.2"

class Test : CliktCommand() {

    init {
        configureContext {
            terminal = Terminal(width = 240)
            helpFormatter = { ArgumentsFirstHelpFormatter(it) }
        }
    }

    val os: OS by argument().enum<OS>(ignoreCase = true, key = { it.name.lowercase() })
        .help("android or ios")
    val iOSVersion: String? by option("--ios", "--iOSVersion")
        .help("iOS version number (ex: $DEFAULT_IOS_VERSION).")
    val appSource: AppSource by argument("app type or template")
        .help("An app type (${AppType.entries.joinToString(", ") { it.name.lowercase() }}) " +
                "or a template URL/name")
        .convert { input ->
            val appType = AppType.entries.find { it.name.equals(input, ignoreCase = true) }
            if (appType != null) {
                AppSource.ByType(os, type = appType)
            } else {
                AppSource.ByTemplate(os, template = input)
            }
        }
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
    val print = Printer { message, err -> echo(message, err = err) }

    override fun run() {
        utilVerboseOutput = verboseOutput || IS_CI

        when(os) {
            OS.ANDROID -> {
                if (iOSVersion != null) {
                    throw UsageError("--iOSVersion can only be used with iOS")
                }
                if (appSource.appName.contains("swift")) {
                    throw UsageError("Swift apps can only be built with iOS")
                }
            }
            OS.IOS -> {
                if (appSource.appName.contains("kotlin")) {
                    throw UsageError("Kotlin apps can only be built with Android")
                }
            }
        }

        val appInfo = if (!reRunTest) {
            generateApp(appSource, useSF, print)
        } else {
            getAppInfo(appSource)
        }

        compileApp(appInfo, debug, log = print)
        runTests(appInfo, iOSVersion ?: DEFAULT_IOS_VERSION, useFirebase, print)
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

class Printer(private val echo: (String, Boolean) -> Unit) {
    operator fun invoke(message: String, err: Boolean = false) = echo(message, err)
}