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
package com.salesforce.util

import java.io.File
import java.lang.ProcessBuilder.Redirect.INHERIT

var verboseCommandOutput = false

class CommandException(message: String) : Exception(message)

data class CommandResult(val exitCode: Int, val output: String?) {
    fun saveFullOutput(appPath: String, label: String): String? {
        if (verboseCommandOutput || output.isNullOrBlank()) return null
        val sanitizedLabel = label.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val logFile = File(appPath, "${sanitizedLabel}_output.log").canonicalFile
        logFile.writeText(output)
        return logFile.absolutePath
    }

    fun throwIfFailed(appPath: String, label: String, message: String) {
        if (exitCode == 0) return
        val logPath = saveFullOutput(appPath, label)
        val logMsg = logPath?.let { "\n\nFull command output saved to: $it" } ?: ""
        throw CommandException("$message$logMsg")
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
    if (this.first().contains("adb")) {
        val devices = getAdbDevices()
        if (devices.size > 1) {
            var lastResult = CommandResult(0, null)
            for (device in devices) {
                val adbArgs = listOf(this.first(), "-s", device) + this.drop(1)
                lastResult = adbArgs.runSingleCapture(workingDir)
                if (lastResult.exitCode != 0) return lastResult
            }
            return lastResult
        }
    }

    return runSingleCapture(workingDir)
}

private fun List<String>.runSingleCapture(workingDir: String = "."): CommandResult {
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
            return devices.maxOfOrNull { device ->
                val adbArgs = listOf(this.first(), "-s", device) + this.drop(1)
                adbArgs.runSingleCommand(workingDir, suppressErrors)
            } ?: 0
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
        val logFile = File.createTempFile("command_failure_", ".log")
        logFile.writeText(capturedOutput)
        System.err.println("Command failed (exit $exitCode). Full output saved to: ${logFile.absolutePath}")
    }

    return exitCode
}