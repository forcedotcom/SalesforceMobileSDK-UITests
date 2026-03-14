package com.salesforce.Util

import java.io.File
import java.lang.ProcessBuilder.Redirect.INHERIT

fun String.runCommand(workingDir: String = ".", verbose: Boolean = utilVerboseOutput): Int =
    split(" ").runCommand(workingDir, verbose)

fun List<String>.runCommand(workingDir: String = ".", verbose: Boolean = utilVerboseOutput): Int {
    val isAdb = this.first().contains("adb")

    if (isAdb) {
        val devices = getAdbDevices()
        if (devices.size > 1) {
            return devices.map { device ->
                val adbArgs = listOf(this.first(), "-s", device) + this.drop(1)
                adbArgs.runSingleCommand(workingDir, verbose)
            }.maxOrNull() ?: 0
        }
    }

    return runSingleCommand(workingDir, verbose)
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

private fun List<String>.runSingleCommand(workingDir: String, verbose: Boolean): Int {
    val command = if (this.first().contains("xcodebuild")) {
        listOf("/bin/bash", "-c", "set -o pipefail && ${joinToString(" ") { if (' ' in it) "'$it'" else it }} | xcbeautify")
    } else {
        this
    }

    val process = ProcessBuilder(command)
        .directory(File(workingDir))
        .apply {
            if (verbose) {
                redirectOutput(INHERIT)
                redirectError(INHERIT)
            } else {
                redirectErrorStream(true)
            }
        }
        .start()

    val capturedOutput = if (!verbose) process.inputStream.bufferedReader().readText() else null
    val exitCode = process.waitFor()

    if (!verbose && exitCode != 0 && !capturedOutput.isNullOrBlank()) {
        System.err.print(capturedOutput)
    }

    return exitCode
}