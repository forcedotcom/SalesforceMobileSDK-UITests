package com.salesforce.util

import java.io.File
import java.lang.ProcessBuilder.Redirect.INHERIT

var verboseCommandOutput = false

fun String.runCommand(workingDir: String = ".", suppressErrors: Boolean = false): Int =
    split(" ").runCommand(workingDir, suppressErrors)

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