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
fun performUpgrade(appSource: AppSource, useSF: Boolean, debug: Boolean) {
    progressBanner?.update {
        context = context.advance("Re-generate App (dev)")
        completed += 1
    }
    verbosePrinter?.invoke("Re-generating app with dev SDK")

    val newAppInfo = generateApp(appSource, useSF)
    compileApp(newAppInfo, debug)

    runUpgradeTests(newAppInfo, simulators = getRunningTestSimulators())
}

/**
 * Clones the SalesforceMobileSDK-Package repo at the specified branch/tag
 * into a separate directory for generating apps from an older SDK version.
 * Returns the directory name.
 */
fun setupOldPackager(version: String): String {
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
        "https://github.com/forcedotcom/SalesforceMobileSDK-Package.git",
        OLD_PACKAGER_DIR
    ).runCommand()
    if (cloneResult != 0) {
        throw Exception("Failed to clone SalesforceMobileSDK-Package at version '$version'. " +
                "Verify that the branch or tag exists.")
    }

    // Old packager versions (pre-14) use install.js instead of a root package.json
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