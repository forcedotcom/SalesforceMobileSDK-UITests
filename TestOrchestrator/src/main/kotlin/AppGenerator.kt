package com.salesforce

import com.salesforce.util.progress
import com.salesforce.util.runCommand
import com.salesforce.util.verbosePrinter
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString

fun generateApp(appSource: AppSource, useSF: Boolean): AppInfo {
    val generationCommand = mutableListOf(
        "./SalesforceMobileSDK-Package/test/test_force.js",
        "--os=${appSource.osName}"
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
                print("Generating Template App")
                appSource.template
            } else {
                print("Generating ${appSource.template} Template App")
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

    return appInfo
}

private fun setupComplexHybrid(appInfo: AppInfo) {
    val complexType = appInfo.appName.removePrefix("complex_hybrid")
    progress?.update {
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
        )
    }
}
