package com.salesforce

import com.salesforce.util.runCommand
import com.salesforce.util.verbosePrinter
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
            print("Generating ${appSource.type.scriptValue} App")
            generationCommand.add("--appType=${appSource.type.scriptValue}")

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

    return getAppInfo(appSource)
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
