package com.salesforce.util

import java.io.File
import java.lang.ProcessBuilder.Redirect.INHERIT

var verboseCommandOutput = false

data class CommandResult(val exitCode: Int, val output: String?) {
    fun saveFullOutput(appPath: String, label: String): String? {
        if (verboseCommandOutput || output.isNullOrBlank()) return null
        val sanitizedLabel = label.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val logFile = File(appPath, "${sanitizedLabel}_output.log")
        logFile.writeText(output)
        return logFile.absolutePath
    }

    fun parseBuildFailure(): String {
        if (output.isNullOrBlank()) return "Build failed with no output."

        val lines = output.lines()
        val errorLines = mutableListOf<String>()

        var capturingFailedCommands = false
        for (line in lines) {
            val trimmed = line.trim()
            when {
                // C++/Swift/Kotlin compilation errors (raw xcodebuild and xcbeautify ❌ format)
                trimmed.contains(": error:") -> { errorLines.add(trimmed); capturingFailedCommands = false }
                // xcbeautify emoji error lines
                trimmed.startsWith("❌") -> { errorLines.add(trimmed); capturingFailedCommands = false }
                // Xcode build failure summary
                trimmed == "** BUILD FAILED **" -> { errorLines.add(trimmed); capturingFailedCommands = false }
                // Xcode "The following build commands failed:" section
                trimmed.startsWith("The following build commands failed:") -> {
                    errorLines.add(trimmed)
                    capturingFailedCommands = true
                }
                capturingFailedCommands && trimmed.isNotEmpty() -> errorLines.add(trimmed)
                capturingFailedCommands && trimmed.isEmpty() -> capturingFailedCommands = false
                // Gradle/Xcode task failures
                trimmed.startsWith("FAILURE:") || trimmed.startsWith("* What went wrong:") -> errorLines.add(trimmed)
                // Execution failed messages
                trimmed.startsWith("Execution failed for task") -> errorLines.add(trimmed)
                // Raw xcodebuild error prefix
                trimmed.startsWith("error:") -> errorLines.add(trimmed)
            }
        }

        return if (errorLines.isNotEmpty()) {
            errorLines.joinToString("\n")
        } else {
            lines.filter { it.isNotBlank() }.takeLast(10).joinToString("\n") { it.trim() }
        }
    }
}

fun String.runCommand(workingDir: String = ".", suppressErrors: Boolean = false): Int =
    split(" ").runCommand(workingDir, suppressErrors)

fun List<String>.runCommandCapture(workingDir: String = "."): CommandResult {
    val command = if (this.first().contains("xcodebuild")) {
        listOf("/bin/bash", "-c", "set -o pipefail && ${joinToString(" ") { if (' ' in it) "'$it'" else it }} | xcbeautify")
    } else {
        this
    }

    val process = ProcessBuilder(command)
        .directory(File(workingDir))
        .redirectErrorStream(true)
        .start()

    val output = StringBuilder()
    process.inputStream.bufferedReader().useLines { lines ->
        for (line in lines) {
            if (verboseCommandOutput) println(line)
            output.appendLine(line)
        }
    }
    val exitCode = process.waitFor()
    return CommandResult(exitCode, output.toString())
}

fun List<String>.runCommand(workingDir: String = ".", suppressErrors: Boolean = false): Int {
    val isAdb = this.first().contains("adb")

    if (isAdb) {
        val devices = getAdbDevices()
        if (devices.size > 1) {
            return devices.map { device ->
                val adbArgs = listOf(this.first(), "-s", device) + this.drop(1)
                adbArgs.runSingleCommand(workingDir, suppressErrors)
            }.maxOrNull() ?: 0
        }
    }

    return runSingleCommand(workingDir, suppressErrors)
}

private fun getAdbDevices(): List<String> {
    val process = ProcessBuilder("adb", "devices")
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()
    return output.lines()
        .drop(1) // skip "List of devices attached" header
        .filter { it.contains("\tdevice") }
        .map { it.split("\t").first() }
}

private fun List<String>.runSingleCommand(workingDir: String, suppressErrors: Boolean = false): Int {
    val command = if (this.first().contains("xcodebuild")) {
        listOf("/bin/bash", "-c", "set -o pipefail && ${joinToString(" ") { if (' ' in it) "'$it'" else it }} | xcbeautify")
    } else {
        this
    }

    val process = ProcessBuilder(command)
        .directory(File(workingDir))
        .apply {
            if (verboseCommandOutput) {
                redirectOutput(INHERIT)
                redirectError(INHERIT)
            } else {
                redirectErrorStream(true)
            }
        }
        .start()

    val capturedOutput = if (!verboseCommandOutput) process.inputStream.bufferedReader().readText() else null
    val exitCode = process.waitFor()

    if (!suppressErrors && !verboseCommandOutput && exitCode != 0 && !capturedOutput.isNullOrBlank()) {
        System.err.print(capturedOutput)
    }

    return exitCode
}