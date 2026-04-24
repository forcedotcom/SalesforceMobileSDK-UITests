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

import com.salesforce.util.progressBanner
import com.salesforce.util.runCommand
import com.salesforce.util.runCommandCapture
import com.salesforce.util.verbosePrinter
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString

fun generateApp(
    appSource: AppSource,
    useSF: Boolean,
    packagerDir: String = "SalesforceMobileSDK-Package",
    packagerVersion: String? = null,
    org: String = FORCE_DOT_COM_ORG,
): AppInfo {
    val generationCommand = mutableListOf(
        "./$packagerDir/test/test_force.js",
        "--os=${appSource.osName}",
        "--exit-on-failure"
    )

    when(appSource) {
        is AppSource.ByType -> {
            verbosePrinter?.invoke("Generating ${appSource.type.scriptValue} App")
            val type = if (appSource.isComplexHybrid) "hybrid_local" else appSource.type.scriptValue
            generationCommand.add("--appType=$type")

            if (appSource.isHybrid) {
                generationCommand.add("--no-plugin-update")
            }
        }
        is AppSource.ByTemplate -> {
            val templateUrl: String = if (appSource.template.startsWith("https")) {
                verbosePrinter?.invoke("Generating Template App")
                appSource.template
            } else if (packagerVersion != null) {
                // Old packager for upgrade testing: use the version tag so the
                // old SDK templates are cloned (e.g. #v13.1.1).
                verbosePrinter?.invoke("Generating ${appSource.template} Template App ($packagerVersion)")
                "https://github.com/$org/SalesforceMobileSDK-Templates/${appSource.template}#$packagerVersion"
            } else {
                verbosePrinter?.invoke("Generating ${appSource.template} Template App")
                "https://github.com/forcedotcom/SalesforceMobileSDK-Templates/${appSource.template}#\\dev"
            }

            generationCommand.add("--templaterepouri=$templateUrl")
        }
    }

    if (useSF) {
        generationCommand.add("--use-sfdx")
    }

    if (generationCommand.runCommand() != 0) {
        throw Exception("Unable to generate app.")
    }

    val appInfo = getAppInfo(appSource)

    if (appSource.isComplexHybrid) {
        setupComplexHybrid(appInfo)
    }

    if (appSource.isReact) {
        setupReactNative(appInfo)
    }

    return appInfo
}

private fun setupComplexHybrid(appInfo: AppInfo) {
    val complexType = appInfo.complexHybridType
        ?: throw Exception("Complex hybrid type not set on AppInfo.")
    progressBanner?.update {
        context = context.advance("Setup Complex Hybrid")
        completed += 1
    }
    verbosePrinter?.invoke("Setting up complex hybrid: $complexType")

    val sharedDir = File("SalesforceMobileSDK-Shared")
    if (!sharedDir.exists()) {
        val cloneResult = "git clone --branch dev --single-branch --depth 1 https://github.com/forcedotcom/SalesforceMobileSDK-Shared.git"
            .runCommand()
        if (cloneResult != 0) {
            throw Exception("Failed to clone SalesforceMobileSDK-Shared.")
        }
    }

    val sampleDir = File("SalesforceMobileSDK-Shared/samples/$complexType")
    if (!sampleDir.exists()) {
        throw Exception("Complex hybrid sample '$complexType' not found at ${sampleDir.path}")
    }

    val wwwDir = File(appInfo.appPath, "www")
    sampleDir.listFiles()?.forEach { file ->
        file.copyRecursively(File(wwwDir, file.name), overwrite = true)
    }

    val cordovaResult = "cordova prepare".runCommand(workingDir = appInfo.appPath)
    if (cordovaResult != 0) {
        throw Exception("Cordova prepare failed for complex hybrid.")
    }
}

private fun setupReactNative(appInfo: AppInfo) {
    progressBanner?.update {
        context = context.advance("Setup React Native")
        completed += 1
    }
    verbosePrinter?.invoke("Setting up React Native")

    if (appInfo.os == OS.IOS) {
        // Delete stale Gemfile.lock to avoid bundler version conflicts
        // (generated lockfile pins Bundler 1.17.2 which is incompatible with Ruby 3.2+)
        File(appInfo.appPath, "Gemfile.lock").delete()
        val bundleResult = listOf("bundle", "install")
            .runCommandCapture(workingDir = appInfo.appPath)
        bundleResult.throwIfFailed(appInfo.appPath, "bundle_install", "Bundle install failed.\n${bundleResult.parseBuildFailure()}")
    }

    // Run install script (handles pod install/update for iOS, npm setup for both)
    val installScripts = File(appInfo.appPath).listFiles { file ->
        file.name.startsWith("install") && file.name.endsWith(".js")
    }
    installScripts?.firstOrNull()?.let { script ->
        val result = "./${script.name}".runCommand(workingDir = appInfo.appPath)
        if (result != 0) {
            throw Exception("React Native install script failed.")
        }
    }

    if (appInfo.os == OS.ANDROID) {
        // This is for older versions and a no-op for dev templates.
        patchReactNativeAutolinking(appInfo)

        // Create assets directory and bundle JS
        val assetsDir = File(appInfo.androidRoot, "app/src/main/assets")
        assetsDir.mkdirs()

        val bundleResult = listOf(
            "npx", "react-native", "bundle",
            "--platform", "android",
            "--dev", "false",
            "--entry-file", "index.js",
            "--bundle-output", "android/app/src/main/assets/index.android.bundle"
        ).runCommandCapture(workingDir = appInfo.appPath)
        bundleResult.throwIfFailed(appInfo.appPath, "react_native_bundle", "React Native bundle failed.\n${bundleResult.parseBuildFailure()}")
    }
}

/**
 * Fixes React Native library Android builds that are incompatible with
 * the current SDK / AGP / React Native version.
 *
 * Older templates bundle library versions that can fail in two ways:
 * 1. AGP classpath conflict — the library's `buildscript` pins an older
 *    AGP, producing "No variants exist" errors.
 * 2. API incompatibility — the library's native code references classes
 *    removed in newer React Native versions (e.g. ViewManagerWithGeneratedInterface).
 *
 * Detection: if any library's android/build.gradle contains its own AGP
 * classpath declaration we know this is an old template.  In that case
 * we update *all* known-problematic libraries to their latest releases.
 * For current dev templates none of the checks trigger and this is a no-op.
 */
private fun patchReactNativeAutolinking(appInfo: AppInfo) {
    val nodeModules = File(appInfo.appPath, "node_modules")
    val agpClasspath = Regex("""com\.android\.tools\.build:gradle""")

    // Libraries that may have build or API incompatibilities with the
    // current SDK when coming from an older template.
    val libraries = listOf(
        "react-native-screens",
        "react-native-vector-icons",
        "react-native-gesture-handler",
    )

    // Detect old template: any library's android/build.gradle pins its own AGP.
    val isOldTemplate = libraries.any { library ->
        val buildFile = File(nodeModules, "$library/android/build.gradle")
        buildFile.exists() && agpClasspath.containsMatchIn(buildFile.readText())
    }

    if (!isOldTemplate) return

    val librariesToUpdate = libraries.filter { File(nodeModules, it).exists() }

    verbosePrinter?.invoke(
        "Old template detected — updating libraries: ${librariesToUpdate.joinToString()}"
    )

    val packages = librariesToUpdate.map { "$it@latest" }
    val yarnResult = (listOf("yarn", "add") + packages)
        .runCommandCapture(workingDir = appInfo.appPath)

    if (yarnResult.exitCode != 0) {
        verbosePrinter?.invoke("yarn add failed, trying npm install...")
        val npmResult = (listOf("npm", "install", "--legacy-peer-deps") + packages)
            .runCommandCapture(workingDir = appInfo.appPath)
        npmResult.throwIfFailed(
            appInfo.appPath,
            "update_rn_libs",
            "Failed to update React Native libraries for compatibility.\n${npmResult.output?.take(500)}"
        )
    }
}

fun relocateApp(appInfo: AppInfo, version: String): AppInfo {
    val versionDir = File(version)
    versionDir.mkdirs()
    val destination = File(versionDir, appInfo.appName)
    if (destination.exists()) destination.deleteRecursively()
    File(appInfo.appPath).renameTo(destination)
    verbosePrinter?.invoke("Moved ${appInfo.appName} to $version/")
    return appInfo.copy(appPath = destination.path)
}

fun getAppInfo(appSource: AppSource): AppInfo {
    val tmpDirs = Path(".").listDirectoryEntries()
        .filter { it.fileName.toString().startsWith("tmp") }
    val appDirs = tmpDirs.flatMap { it.listDirectoryEntries() }
    val path = appDirs.firstOrNull { it.fileName.toString() == appSource.appName }
        ?: throw Exception(
            "Could not find app directory for '${appSource.appName}'. " +
            "tmp dirs: ${tmpDirs.map { it.fileName }}, " +
            "app dirs: ${appDirs.map { it.fileName }}"
        )
    val pathString = path.pathString

    with(appSource) {
        return AppInfo(
            os = os,
            appName = appName,
            appPath = pathString,
            packageName = "com.salesforce.$appName",
            isHybrid = isHybrid,
            isReact = isReact,
            complexHybridType = complexHybridName,
        )
    }
}
