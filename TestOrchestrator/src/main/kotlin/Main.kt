/*
 * Copyright (c) 2026-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
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
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
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
import com.salesforce.util.detectTerminalWidth
import com.salesforce.util.finish
import com.salesforce.util.progressBanner
import com.salesforce.util.startBackgroundRuntimeInstalls
import com.salesforce.util.verboseCommandOutput
import com.salesforce.util.verbosePrinter
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.system.exitProcess

const val ANDROID_MIN_API_LEVEL = 28
const val ANDROID_MAX_API_LEVEL = 36
const val DEFAULT_IOS_VERSION = "26"
const val DEFAULT_IOS_DEVICE = "iPhone-SE-3rd-generation"
const val OLD_PACKAGER_DIR = "SalesforceMobileSDK-Package-Old"
const val FORCE_DOT_COM_ORG = "forcedotcom"

// Aliases for templates whose repo name doesn't match the convention used
// by the rest of the templates (e.g. MobileSyncExplorer{Swift,ReactNative}
// have no "Template" suffix, but MobileSyncExplorerKotlinTemplate does).
private val TEMPLATE_ALIASES = mapOf(
    "mobilesyncexplorerkotlin" to "MobileSyncExplorerKotlinTemplate",
)

private fun resolveTemplateAlias(input: String): String =
    TEMPLATE_ALIASES[input.lowercase()] ?: input


class TestOrchestrator : CliktCommand() {

    init {
        installMordantMarkdown()
        configureContext {
            terminal = Terminal(
                width = detectTerminalWidth(),
                interactive = true,
                hyperlinks = true,
            )
            helpFormatter = { ArgumentsFirstHelpFormatter(it) }
        }
    }

    // Required Arguments
    val os: OS by argument().enum<OS>(ignoreCase = true, key = { it.name.lowercase() })
        .help("android or ios")
    val appSources: Set<AppSource> by argument("app type or template")
        .help("App type (${AppType.entries.joinToString(", ") { it.name.lowercase() }}) " +
                "\u0085or a template URL/name (CamelCase or URL)" +
                "\u0085or a complex hybrid sample name (e.g. accounteditor)" +
                "\u0085(multiple allowed, space separated)")
        .convert { input ->
            val strippedInput = input.removePrefix("complex_hybrid_")
            val normalizedInput = input.replace("_", "")
            val appType = AppType.entries.find {
                it.name.equals(input, ignoreCase = true)
                        || it.scriptValue.equals(input, ignoreCase = true)
                        || it.scriptValue.equals(strippedInput, ignoreCase = true)
                        || it.name.replace("_", "").equals(normalizedInput, ignoreCase = true)
            }
            when {
                appType != null -> AppSource.ByType(os, type = appType)
                else -> AppSource.ByTemplate(os, template = resolveTemplateAlias(input))
            }
        }
        .multiple().unique()

    // Options
    val debug: Boolean by option("-d", "--compileDebug").flag()
        .help("Compile and use the debug configuration of the generated app(s).")
    val iOSVersions: List<String> by option("--ios", "--iOSVersion").multiple()
        .help("iOS version to test. If only the major version is provided, the highest available minor version is used." +
                "\u0085Multiple allowed with repeated flag or single quoted space separated list. " +
                "\u0085(ex: --iOS=18.5 --iOS=18.6 or --iOS \"17 18 26\")")
    val iOSDevice: String? by option("--device", "--iOSDevice")
        .help("iOS Simulator device type.  Uses SimDeviceType identifier format.  (ex: $DEFAULT_IOS_DEVICE)")
    val reRunTest: Boolean by option("-r", "--reRun").flag()
        .help("Run the validation test again without re-generating the app.")
    val useSF: Boolean by option("--sf", "--sfdx").flag()
        .help("Use SF (formerly SFDX) to generate the app.")
    val preserverGeneratedApps: Boolean by option("-p", "--preserverGeneratedApps").flag()
        .help("Do not cleanup generated apps from previous runs.")
    val upgradeFrom: String? by option("-u", "--upgrade", "--upgradeFrom")
        .help("Run an upgrade test. Provide the SDK version (as a branch or tag) to upgrade FROM (e.g. 'v12.1.0')." +
                "\u0085The app is generated with this version, logged in, then upgraded to dev and verified.")
    val sdkVersion: String? by option("--sdk", "--sdkVersion")
        .help("Generate and test the app using a specific SDK branch or tag (e.g. 'master', 'v13.2.0')." +
                "\u0085You can optionally specify the Github org with a '/' (e.g 'brandonpage/my-feature-branch')")
    val useFirebase: Boolean by option("-f", "--firebase").boolean()
        .defaultLazy { IS_CI && upgradeFrom.isNullOrBlank() }
        .help("Run Android tests in Firebase Test Lab. Defaults to on for CI and off otherwise.")
    val verboseOutput: Boolean by option("-v", "--verbose").flag()
        .help("Show all command output. Automatically on for CI.")


    override fun run() {
        val failures = mutableListOf<Pair<String, Exception>>()
        verboseCommandOutput = verboseOutput || IS_CI

        // Support both "--ios 17 --ios 18" and "--ios "17 18"" (space-separated in a single value)
        val effectiveVersions = iOSVersions
            .flatMap { it.split(" ") }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(DEFAULT_IOS_VERSION) }

        when (os) {
            OS.ANDROID -> {
                if (iOSVersions.isNotEmpty()) {
                    throw UsageError("--iOSVersion can only be used with iOS")
                }
                if (iOSDevice != null) {
                    throw UsageError("--device can only be used with iOS")
                }
                if (appSources.find { it.appName.contains("swift") } != null) {
                    throw UsageError("Swift apps can only be built with iOS")
                }
                if (upgradeFrom != null && useFirebase) {
                    throw UsageError("Upgrade testing is not supported with Firebase Test Lab. Use local testing instead.")
                }
            }

            OS.IOS -> {
                if (appSources.find { it.appName.contains("kotlin") } != null) {
                    throw UsageError("Kotlin apps can only be built with Android")
                }
            }
        }

        if (upgradeFrom != null && reRunTest) {
            throw UsageError("--upgrade cannot be combined with --reRun.")
        }

        if (sdkVersion != null && upgradeFrom != null) {
            throw UsageError("--sdkVersion cannot be combined with --upgrade.")
        }
        if (sdkVersion != null && reRunTest) {
            throw UsageError("--sdkVersion cannot be combined with --reRun.")
        }


        if (!reRunTest) {
            val tmpDirs = File(".").listFiles { files ->
                files.isDirectory && files.name.startsWith("tmp")
            } ?: emptyArray()
            if (!preserverGeneratedApps) {
                // Remove all previous generations
                tmpDirs.forEach { it.deleteRecursively() }
            } else {
                // Remove only the specified apps to force regeneration
                tmpDirs.forEach { tmpDir ->
                    appSources.forEach { appSource ->
                        File(tmpDir, appSource.appName).takeIf {
                            it.exists()
                        }?.deleteRecursively()
                    }
                }
            }
        }

        // On CI, set verbose printer early so background install logs are visible
        if (IS_CI) {
            verbosePrinter = Printer(terminal)
        }

        appSources.forEach { appSource ->
            if (verboseOutput || IS_CI) {
                verbosePrinter = Printer(terminal)
            } else {
                val marker = PanelProgressBarMaker
                marker.title = when(os) {
                    OS.IOS -> "Testing ${appSource.appName} (iOS ${effectiveVersions.joinToString(", ")})"
                    OS.ANDROID -> "Testing ${appSource.appName}"
                }
                var totalSteps: Long = when(os) {
                    OS.ANDROID -> if (reRunTest) 3 else 7
                    OS.IOS -> {
                        val base = if (reRunTest) 2 else 5
                        // Add steps per iOS version being tested
                        base + (3L * effectiveVersions.size)
                    }
                }
                if (appSource.isReact) {
                    totalSteps++
                }
                if (appSource.isComplexHybrid && !reRunTest) {
                    totalSteps++
                }
                if (upgradeFrom != null) {
                    // Upgrade adds: clone old packager (Phase 1), plus Phase 2:
                    // re-generate, set login URL, set OAuth, compile, sign (Android only),
                    // install upgrade, run upgrade test, pass
                    totalSteps += when(os) {
                        OS.ANDROID -> 8L
                        OS.IOS -> 6L + effectiveVersions.size
                    }
                    if (appSource.isComplexHybrid) totalSteps++
                    if (appSource.isReact) totalSteps++
                }
                if (sdkVersion != null) {
                    // sdkVersion adds: clone packager step
                    totalSteps++
                }

                progressBanner = progressBarContextLayout<ProgressState> {
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

                    text { if (context.testPassed) "\nCompleted:" else "" }
                    text {
                        if (context.testPassed) {
                            TextColors.green(
                                text = if (upgradeFrom != null) {
                                    "\nUpgrade Test Passed!"
                                } else {
                                    "\nLogin Test Passed!"
                                }
                            )
                        } else ""
                    }
                    text { if (context.error != null) "\nError:" else "" }
                    text { if (context.error != null) TextColors.red("\n${context.error!!}") else "" }
                }.animateOnThread(
                    terminal,
                    context = ProgressState(
                        currentStep = when {
                            upgradeFrom != null -> "Clone Packager ($upgradeFrom)"
                            sdkVersion != null -> "Clone Packager ($sdkVersion)"
                            else -> "Generate App"
                        }
                    ),
                    total = totalSteps,
                    maker = marker,
                ).also { it.execute() }
            }

            try {
                val appInfo = if (!reRunTest) {
                    if (upgradeFrom != null) {
                        // Upgrade Phase 1: Generate with old SDK version
                        val oldPackager = setupOldPackager(upgradeFrom!!)
                        val oldAppInfo = generateApp(
                            appSource,
                            useSF,
                            packagerDir = oldPackager,
                            packagerVersion = upgradeFrom,
                        )
                        relocateApp(oldAppInfo, upgradeFrom!!)
                    } else if (sdkVersion != null) {
                        // Version test: Generate with specific SDK branch/tag
                        // Support fork syntax: "owner/branch" or plain "branch"
                        val (sdkOrg, sdkBranch) = if ('/' in sdkVersion!!) {
                            sdkVersion!!.substringBefore('/') to sdkVersion!!.substringAfter('/')
                        } else {
                            FORCE_DOT_COM_ORG to sdkVersion!!
                        }
                        val packager = setupOldPackager(sdkBranch, org = sdkOrg)
                        generateApp(
                            appSource,
                            useSF,
                            packagerDir = packager,
                            packagerVersion = sdkBranch,
                            org = sdkOrg,
                        )
                    } else {
                        generateApp(appSource, useSF)
                    }
                } else {
                    verbosePrinter?.invoke("Skipping App Generation")
                    getAppInfo(appSource)
                }

                // Start runtime downloads after app generation to avoid rsync
                // conflicts with CocoaPods. Downloads still overlap with xcodebuild.
                if (os == OS.IOS && IS_CI) {
                    startBackgroundRuntimeInstalls(effectiveVersions, iOSDevice ?: DEFAULT_IOS_DEVICE)
                }

                if (!reRunTest) {
                    compileApp(appInfo, debug)
                }

                // Run login test (installs app, logs in, asserts app loads)
                runTests(
                    appInfo,
                    iOSVersions = effectiveVersions,
                    iOSDevice ?: DEFAULT_IOS_DEVICE,
                    useFirebase,
                    finishProgress = upgradeFrom == null,
                    upgradeLogin = upgradeFrom != null,
                )

                // Upgrade Phase 2: Upgrade test
                if (upgradeFrom != null) {
                    performUpgrade(appSource, useSF, debug)
                }
            } catch (e: Exception) {
                failures.add(appSource.appName to e)
                progressBanner?.update {
                    context = context.fail(e.message ?: e.toString())
                }
                // Give the animation thread time to render the fail frame with
                // the error text before stopping it, otherwise the banner can
                // freeze on the in-progress frame and hide the error in
                // non-verbose mode.
                Thread.sleep(2_000)
                progressBanner?.finish()
                verbosePrinter?.invoke("${appSource.appName} failed: ${e.message ?: e.toString()}", err = true)
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
        const val ANDROID_TEST_CLASS_DIR = "com.salesforce.mobilesdk.mobilesdkuitest.login"
        const val IOS_TEST_DIR = "./iOS/"
        const val SIM_NAME = "testsim"
        val GCLOUD_RESULTS_DIR: String? by lazy { System.getenv("GCLOUD_RESULTS_DIR") }

        val IS_CI: Boolean by lazy { !System.getenv("GITHUB_WORKFLOW").isNullOrBlank() }
    }
}

fun main(args: Array<String>) = TestOrchestrator().main(args)