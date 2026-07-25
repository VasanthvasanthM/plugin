package com.logflow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class LogToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val logPanel = LogPanel()
        val content = ContentFactory.getInstance().createContent(logPanel.getContent(), "Logs", false)
        toolWindow.contentManager.addContent(content)
    }
}