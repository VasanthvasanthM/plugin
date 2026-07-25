package com.logflow

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

class LogStreamer(
    private val host: String,
    private val port: Int,
    private val user: String,
    private val pass: String,
    private val filePath: String,
    private val tailCount: Int,
    private val getErrorPatterns: () -> List<String>,
    private val onNewLine: (String, Boolean) -> Unit,
    private val onErrorDetected: (String) -> Unit,
    private val onStopped: () -> Unit
) {
    private val isRunning = AtomicBoolean(false)
    private var thread: Thread? = null

    fun start() {
        if (isRunning.get()) return
        isRunning.set(true)

        thread = Thread {
            try {
                val jsch = JSch()
                val session = jsch.getSession(user, host, port).apply {
                    setPassword(pass)
                    setConfig("StrictHostKeyChecking", "no")
                    connect(10000)
                }

                val channel = session.openChannel("exec") as ChannelExec

                val isWindows = filePath.contains(":\\") || filePath.contains("\\")

                val command = if (isWindows) {
                    val formattedPath = filePath.replace("/", "\\")
                    "powershell -Command \"Get-Content -Path '$formattedPath' -Wait -Tail $tailCount\""
                } else {
                    "tail -n $tailCount -f $filePath"
                }

                channel.setCommand(command)

                val inputStream = channel.inputStream
                channel.connect()

                val reader = BufferedReader(InputStreamReader(inputStream))

                while (isRunning.get()) {
                    val line = reader.readLine()
                    if (line != null) {
                        val currentPatterns = getErrorPatterns()
                        val isError = currentPatterns.any { pattern ->
                            pattern.isNotBlank() && line.contains(pattern.trim(), ignoreCase = true)
                        }

                        onNewLine(line, isError)

                        if (isError) {
                            onErrorDetected(line)
                        }
                    } else {
                        Thread.sleep(50)
                    }
                }

                channel.disconnect()
                session.disconnect()
            } catch (e: Exception) {
                if (isRunning.get()) {
                    onNewLine("[LogFlow Error] Connection/Stream error: ${e.message}", true)
                }
            } finally {
                onStopped()
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        isRunning.set(false)
        thread?.interrupt()
    }
}