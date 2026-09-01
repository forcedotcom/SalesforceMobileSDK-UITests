package com.salesforce

import com.salesforce.util.progressBanner
import com.salesforce.util.runCommand
import com.salesforce.util.runCommandCapture
import com.salesforce.util.verbosePrinter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

/**
 * Phase 2 of upgrade testing: re-generate the app with the current (dev) SDK,
 * re-compile it, install it over the old version, and run the upgrade test
 * which asserts the user is still logged in.
 */
fun performUpgrade(
    appSource: AppSource,
    useSF: Boolean,
    debug: Boolean,
    upgradeFrom: String,
    appConfig: KnownAppConfig = KnownAppConfig.ECA_OPAQUE,
) {
    // Stop any lingering Gradle daemons from Phase 1 to reclaim memory
    // before Phase 2 compilation starts alongside the running emulator.
    if (appSource.os == OS.ANDROID) {
        verbosePrinter?.invoke("Stopping Gradle daemons to free memory for Phase 2")
        "./gradlew --stop".runCommand(workingDir = TestOrchestrator.ANDROID_TEST_DIR)
    }

    progressBanner?.update {
        context = context.advance("Re-generate App (dev)")
        completed += 1
    }
    verbosePrinter?.invoke("Re-generating app with dev SDK")

    val newAppInfo = generateApp(appSource, useSF, appConfig = appConfig)
    // LEGACY UPGRADE AUTOMATION (SDK 12.x ONLY): v12.2.0 hybrid session restoration needs the
    // startup workaround below. v13.2.1 does not use it. Remove this branch with 12.x coverage.
    if (upgradeFrom.startsWith("v12.")) {
        applySdk12IosHybridUpgradeStartupWorkaround(newAppInfo)
    }
    compileApp(newAppInfo, debug)

    val simulators = if (appSource.os == OS.IOS) getRunningTestSimulators() else emptyList()
    runUpgradeTests(newAppInfo, simulators)
}

/**
 * LEGACY UPGRADE AUTOMATION (SDK 12.x ONLY): Keeps the current iOS hybrid app paintable when it
 * inherits an authenticated session from v12.2.0. Remove this function with 12.x hybrid coverage;
 * v13.2.1 does not use it.
 *
 * Restoring the old session can synchronously replace the root view controller during scene
 * activation. UIKit then contains a fully loaded Cordova page that never paints. Deferring that
 * replacement by one main-queue turn avoids the activation race. Normal generation is
 * intentionally untouched.
 */
private fun applySdk12IosHybridUpgradeStartupWorkaround(appInfo: AppInfo) {
    if (appInfo.os != OS.IOS || !appInfo.isHybrid) return

    val appDelegate = File(
        appInfo.iosRoot,
        "App/Plugins/com.salesforce/AppDelegate.swift",
    )
    if (!appDelegate.exists()) {
        throw IllegalStateException(
            "Cannot apply legacy iOS hybrid upgrade workaround: generated AppDelegate not found " +
                "at ${appDelegate.path}.",
        )
    }

    val content = appDelegate.readText()
    val originalFunction =
        """    private func setupRootViewController() {
        |        let config = SalesforceHybridSDKManager.shared.bootConfig as? SFHybridViewConfig
        |        viewController = SFHybridViewController(config: config)
        |        window?.rootViewController = viewController
        |    }
        |""".trimMargin()
    val functionCount = content.windowed(originalFunction.length).count { it == originalFunction }
    if (functionCount != 1) {
        throw IllegalStateException(
            "Cannot apply legacy iOS hybrid upgrade workaround to ${appDelegate.path}: " +
                "expected exactly one setupRootViewController function, found $functionCount.",
        )
    }

    val deferredFunction =
        """    private func setupRootViewController() {
        |        DispatchQueue.main.async { [weak self] in
        |            guard let self else {
        |                return
        |            }
        |
        |            let config = SalesforceHybridSDKManager.shared.bootConfig as? SFHybridViewConfig
        |            self.viewController = SFHybridViewController(config: config)
        |            self.window?.rootViewController = self.viewController
        |        }
        |    }
        |""".trimMargin()
    appDelegate.writeText(content.replace(originalFunction, deferredFunction))
}

/**
 * Clones the SalesforceMobileSDK-Package repo at the specified branch/tag
 * into a separate directory for generating apps from an older SDK version.
 * Returns the directory name.
 */
fun setupOldPackager(version: String, org: String = FORCE_DOT_COM_ORG): String {
    val dir = File(OLD_PACKAGER_DIR)
    if (dir.exists()) {
        dir.deleteRecursively()
    }

    progressBanner?.update {
        context = context.advance("Generate App ($version)")
        completed += 1
    }
    verbosePrinter?.invoke("Cloning SalesforceMobileSDK-Package at $version")

    val cloneResult = listOf(
        "git", "clone",
        "--branch", version,
        "--single-branch", "--depth", "1",
        "https://github.com/$org/SalesforceMobileSDK-Package.git",
        OLD_PACKAGER_DIR,
    ).runCommand()
    if (cloneResult != 0) {
        throw Exception("Failed to clone SalesforceMobileSDK-Package at version '$version'. " +
                "Verify that the branch or tag exists.")
    }

    // Old packager versions (pre-13.2) use install.js instead of a root package.json
    val hasRootPackageJson = File(OLD_PACKAGER_DIR, "package.json").exists()
    val hasInstallJs = File(OLD_PACKAGER_DIR, "install.js").exists()

    val installResult = when {
        hasRootPackageJson -> {
            verbosePrinter?.invoke("Running npm install in $OLD_PACKAGER_DIR")
            listOf("npm", "install", "--legacy-peer-deps")
                .runCommandCapture(workingDir = OLD_PACKAGER_DIR)
        }
        hasInstallJs -> {
            verbosePrinter?.invoke("Running install.js in $OLD_PACKAGER_DIR (legacy packager)")
            // Legacy packager has no root package.json; create a minimal one so
            // npm install (called by install.js) anchors node_modules here
            // instead of walking up the directory tree.
            File(OLD_PACKAGER_DIR, "package.json")
                .writeText("""{"name":"mobilesdk-package-old","private":true}""")
            listOf("node", "install.js")
                .runCommandCapture(workingDir = OLD_PACKAGER_DIR)
        }
        else -> throw Exception(
            "Old packager at '$version' has neither package.json nor install.js. " +
                    "Cannot install dependencies."
        )
    }

    if (installResult.exitCode != 0) {
        throw Exception("Dependency install failed for old packager at version '$version'.\n${installResult.output?.trim()}")
    }

    val nodeModules = File(OLD_PACKAGER_DIR, "node_modules")
    if (!nodeModules.exists()) {
        throw Exception(
            "Dependency install completed but node_modules was not created in $OLD_PACKAGER_DIR.\n" +
                    "Output: ${installResult.output?.trim()}"
        )
    }

    return OLD_PACKAGER_DIR
}

/**
 * Finds simulators from the current test run that are still booted.
 * Used by Phase 2 of upgrade testing to reuse the simulators from Phase 1.
 */
fun getRunningTestSimulators(): List<SimulatorInfo> {
    val process = ProcessBuilder("xcrun", "simctl", "list", "devices", "-j")
        .redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()

    val json = Json.parseToJsonElement(output).jsonObject
    val devices = json["devices"]?.jsonObject ?: return emptyList()

    val simulators = mutableListOf<SimulatorInfo>()
    for ((runtimeKey, deviceList) in devices) {
        for (device in deviceList.jsonArray) {
            val obj = device.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: continue
            val udid = obj["udid"]?.jsonPrimitive?.content ?: continue
            val state = obj["state"]?.jsonPrimitive?.content ?: continue
            if (name.startsWith(TestOrchestrator.SIM_NAME) && state == "Booted") {
                // Extract iOS version from runtime key (e.g. com.apple.CoreSimulator.SimRuntime.iOS-18-2 -> 18.2)
                val version = runtimeKey.substringAfter("iOS-").replace("-", ".")
                simulators.add(SimulatorInfo(udid, version))
            }
        }
    }
    return simulators
}
