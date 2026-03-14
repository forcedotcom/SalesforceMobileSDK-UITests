package com.salesforce

import com.salesforce.Util.runCommand
import com.salesforce.Util.verbosePrinter
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString

fun generateApp(
    appSource: AppSource,
    useSF: Boolean,
    preserverGeneratedApps: Boolean,
): AppInfo {
    if (!preserverGeneratedApps) {
        // Remove previous generations
        File(".").listFiles { files ->
            files.isDirectory && files.name.startsWith("tmp")
        }?.forEach { it.deleteRecursively() }
    }
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

    when(val result = generationCommand.runCommand()) {
        0 -> { verbosePrinter?.success("Success!") }
        else -> {
            verbosePrinter?.invoke("Error: $result", err = true)
            // Throw???
        }
    }

    return getAppInfo(appSource)
}

fun getAppInfo(appSource: AppSource): AppInfo {
    val tmpPath = Path(".").listDirectoryEntries()
        .first { it.fileName.toString().startsWith("tmp") }
        .pathString

    with(appSource) {
        return AppInfo(
            os = os,
            appName = appName,
            appPath = "$tmpPath/$appName",
            packageName = "com.salesforce.$appName",
            isHybrid = isHybrid,
            isReact = isReact,
        )
    }
}
