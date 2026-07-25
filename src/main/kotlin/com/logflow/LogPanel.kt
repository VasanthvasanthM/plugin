package com.logflow

import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.io.File
import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter

class LogPanel {
    private val content = JPanel(BorderLayout())

    private val logPane = JEditorPane().apply {
        contentType = "text/html"
        isEditable = false
        text = "<html><body style='font-family:sans-serif; font-size:12px;'>LogFlow initialized. Configure your server and click Connect...<br></body></html>"
    }

    private val hostField = JTextField("", 12).apply { toolTipText = "Remote SSH Host IP" }
    private val portField = JTextField("22", 4).apply { toolTipText = "SSH Port" }
    private val userField = JTextField("", 8).apply { toolTipText = "SSH Username" }
    private val passField = JPasswordField("", 8).apply { toolTipText = "SSH Password" }
    private val pathField = JTextField("", 22).apply { toolTipText = "Absolute log file path" }

    private val filterField = JTextField("ERROR, WARN, Exception, 400, 500", 20).apply {
        toolTipText = "Comma-separated live error highlight patterns"
    }

    private val tailOptions = arrayOf("20", "50", "100", "500")
    private val tailCombo = JComboBox(tailOptions).apply { selectedItem = "50" }

    private val connectButton = JButton("Connect")
    private val clearButton = JButton("Clear")
    private val exportButton = JButton("Export")
    private val autoScrollCheck = JCheckBox("Auto-scroll", true)

    private val statusLabel = JLabel(" Status: Idle ").apply { isOpaque = false }
    private val statusPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 2)).apply {
        isOpaque = true
        background = Color(240, 240, 240)
        add(statusLabel)
    }

    private var logStreamer: LogStreamer? = null
    private var errorCount = 0
    private var rawLogLines = mutableListOf<String>()
    private var logBuilder = StringBuilder("<html><body style='font-family:sans-serif; font-size:12px;'>LogFlow initialized...<br>")

    init {
        // Row 1: Connection configurations
        val connectionPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5)).apply {
            add(JLabel("Host:"))
            add(hostField)
            add(JLabel("Port:"))
            add(portField)
            add(JLabel("User:"))
            add(userField)
            add(JLabel("Pass:"))
            add(passField)
        }

        // Row 2: File path, filters, options, and actions (Export is safely here)
        val actionPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5)).apply {
            add(JLabel("Path:"))
            add(pathField)
            add(JLabel("Filters:"))
            add(filterField)
            add(JLabel("Tail:"))
            add(tailCombo)
            add(autoScrollCheck)
            add(connectButton)
            add(clearButton)
            add(exportButton)
        }

        val controlPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(connectionPanel)
            add(actionPanel)
        }

        val bottomPanel = JPanel(BorderLayout()).apply {
            add(statusPanel, BorderLayout.CENTER)
        }

        content.add(controlPanel, BorderLayout.NORTH)
        content.add(JBScrollPane(logPane), BorderLayout.CENTER)
        content.add(bottomPanel, BorderLayout.SOUTH)

        clearButton.addActionListener {
            clearLogs()
        }

        exportButton.addActionListener {
            exportLogs()
        }

        connectButton.addActionListener {
            val host = hostField.text.trim()
            val port = portField.text.trim().toIntOrNull() ?: 22
            val user = userField.text.trim()
            val pass = String(passField.password)
            val path = pathField.text.trim()
            val tailCount = (tailCombo.selectedItem as? String)?.toIntOrNull() ?: 50

            if (host.isEmpty() || user.isEmpty() || path.isEmpty()) {
                appendLog("[LogFlow Error] Host, User, and Log Path cannot be empty.", true)
                return@addActionListener
            }

            if (logStreamer != null) {
                logStreamer?.stop()
                logStreamer = null
                return@addActionListener
            }

            clearLogs()
            errorCount = 0
            appendLog("[LogFlow] Connecting to $host:$port (tailing last $tailCount lines)...", false)
            connectButton.text = "Disconnect"
            statusLabel.text = " Status: Streaming Live (No Errors)"
            statusPanel.background = Color(220, 245, 220)

            logStreamer = LogStreamer(
                host = host,
                port = port,
                user = user,
                pass = pass,
                filePath = path,
                tailCount = tailCount,
                getErrorPatterns = {
                    filterField.text.split(",").map { it.trim() }
                },
                onNewLine = { newLine, isError ->
                    SwingUtilities.invokeLater {
                        appendLog(newLine, isError)
                    }
                },
                onErrorDetected = { _ ->
                    SwingUtilities.invokeLater {
                        errorCount++
                        statusLabel.text = " Status: ALERT! Errors Detected: $errorCount"
                        statusPanel.background = Color(255, 220, 220)
                    }
                },
                onStopped = {
                    SwingUtilities.invokeLater {
                        logStreamer = null
                        connectButton.text = "Connect"
                        statusLabel.text = " Status: Disconnected (Total Errors Caught: $errorCount)"
                        statusPanel.background = Color(240, 240, 240)
                        appendLog("[LogFlow] Session stopped.", false)
                    }
                }
            )
            logStreamer?.start()
        }
    }

    private fun clearLogs() {
        rawLogLines.clear()
        logBuilder = StringBuilder("<html><body style='font-family:sans-serif; font-size:12px;'>")
        logPane.text = "$logBuilder</body></html>"
        errorCount = 0
        if (logStreamer == null) {
            statusLabel.text = " Status: Idle "
            statusPanel.background = Color(240, 240, 240)
        }
    }

    private fun exportLogs() {
        if (rawLogLines.isEmpty()) {
            JOptionPane.showMessageDialog(
                content,
                "No logs available to export.",
                "Export Warning",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        val fileChooser = JFileChooser().apply {
            dialogTitle = "Export LogFlow Session"
            addChoosableFileFilter(FileNameExtensionFilter("Log & Text Files (*.log, *.txt, *.out)", "log", "txt", "out"))
            addChoosableFileFilter(FileNameExtensionFilter("JSON & Config Files (*.json, *.yml, *.yaml)", "json", "yml", "yaml"))
            fileFilter = acceptAllFileFilter
            selectedFile = File("logflow-export.log")
        }

        val userSelection = fileChooser.showSaveDialog(content)
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            val fileToSave = fileChooser.selectedFile

            try {
                fileToSave.writeText(rawLogLines.joinToString("\n"))
                JOptionPane.showMessageDialog(
                    content,
                    "File successfully exported to:\n${fileToSave.absolutePath}",
                    "Export Successful",
                    JOptionPane.INFORMATION_MESSAGE
                )
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    content,
                    "Failed to export file: ${e.message}",
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }

    private fun appendLog(text: String, isError: Boolean) {
        rawLogLines.add(text)

        val escapedText = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

        val formattedLine = if (isError) {
            "<span style='background-color: #ffe6e6; color: #b30000; font-weight: bold;'>$escapedText</span><br>"
        } else {
            "$escapedText<br>"
        }

        logBuilder.append(formattedLine)
        logPane.text = "$logBuilder</body></html>"

        if (autoScrollCheck.isSelected) {
            SwingUtilities.invokeLater {
                val docLength = logPane.document.length
                logPane.caretPosition = docLength
                val rect = logPane.modelToView(docLength)
                if (rect != null) {
                    logPane.scrollRectToVisible(rect)
                }
            }
        }
    }

    fun getContent(): JPanel = content
}