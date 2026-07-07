package ai.capps.inspectorcopy

import com.intellij.ide.CopyPasteManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import java.awt.KeyboardFocusManager
import java.awt.datatransfer.StringSelection
import javax.swing.JTree
import javax.swing.tree.TreePath

class CopyForLlmAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val tree = focusedTree() ?: run {
            Messages.showInfoMessage(project, "Focus the Problems or Inspection Results tree and try again.", "Copy for LLM")
            return
        }

        val entries = collectFromTree(project, tree)
        if (entries.isEmpty()) {
            Messages.showInfoMessage(project, "No supported problem nodes were found in the current selection.", "Copy for LLM")
            return
        }

        val markdown = ProblemFormatter.format(entries.distinctBy { Triple(it.filePath, it.lineNumber, it.message) })
        CopyPasteManager.getInstance().setContents(StringSelection(markdown))
        Messages.showInfoMessage(project, "Copied ${entries.size} problem(s) as Markdown.", "Copy for LLM")
    }

    private fun focusedTree(): JTree? {
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner as? JTree
    }

    private fun collectFromTree(project: Project, tree: JTree): List<ProblemEntry> {
        val selectionPaths = tree.selectionPaths?.toList().orEmpty()
        return selectionPaths.mapNotNull { collectEntry(project, it) }
    }

    private fun collectEntry(project: Project, path: TreePath): ProblemEntry? {
        val node = path.lastPathComponent ?: return null
        val userObject = extractUserObject(node)

        val text = node.toString().trim().ifBlank { return null }
        val file = inferFile(project, userObject, text) ?: inferSelectedFile(project)
        val line = inferLine(userObject, text)
        val severity = inferSeverity(text)
        val message = inferMessage(text)
        val filePath = ProblemFormatter.relativize(project, file)
        val snippet = ProblemFormatter.buildSnippet(project, file, line)

        return ProblemEntry(
            filePath = filePath,
            lineNumber = line,
            severity = severity,
            message = message,
            snippet = snippet,
        )
    }

    private fun extractUserObject(node: Any): Any? {
        return runCatching {
            val method = node.javaClass.methods.firstOrNull { it.name == "getUserObject" && it.parameterCount == 0 }
            method?.invoke(node)
        }.getOrNull()
    }

    private fun inferSelectedFile(project: Project): VirtualFile? {
        return CommonDataKeys.VIRTUAL_FILE.getData(com.intellij.openapi.actionSystem.DataManager.getInstance().dataContextFromFocusAsync.blockingGet(200))
            ?: project.guessProjectDir()
    }

    private fun inferFile(project: Project, source: Any?, text: String): VirtualFile? {
        val candidates = buildList {
            if (source != null) add(source)
        }

        candidates.forEach { candidate ->
            extractVirtualFile(candidate)?.let { return it }
            extractPsiFileVirtualFile(candidate)?.let { return it }
        }

        val openFiles = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFiles
        return openFiles.firstOrNull()
    }

    private fun extractVirtualFile(value: Any): VirtualFile? {
        if (value is VirtualFile) return value
        return runCatching {
            val method = value.javaClass.methods.firstOrNull { it.name == "getVirtualFile" && it.parameterCount == 0 }
            method?.invoke(value) as? VirtualFile
        }.getOrNull()
    }

    private fun extractPsiFileVirtualFile(value: Any): VirtualFile? {
        return runCatching {
            val psiFileMethod = value.javaClass.methods.firstOrNull { it.name == "getPsiFile" && it.parameterCount == 0 }
            val psiFile = psiFileMethod?.invoke(value) ?: return null
            val vfMethod = psiFile.javaClass.methods.firstOrNull { it.name == "getVirtualFile" && it.parameterCount == 0 }
            vfMethod?.invoke(psiFile) as? VirtualFile
        }.getOrNull()
    }

    private fun inferLine(source: Any?, text: String): Int {
        val reflected = source?.let { obj ->
            sequenceOf("getLine", "getLineNumber").mapNotNull { methodName ->
                runCatching {
                    val method = obj.javaClass.methods.firstOrNull { it.name == methodName && it.parameterCount == 0 }
                    (method?.invoke(obj) as? Number)?.toInt()
                }.getOrNull()
            }.firstOrNull { it > 0 }
        }

        if (reflected != null) return reflected

        val regex = Regex("(?:^|[^0-9])(\d{1,6})(?:[^0-9]|$)")
        return regex.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
    }

    private fun inferSeverity(text: String): String {
        val lower = text.lowercase()
        return when {
            "error" in lower -> "Error"
            "warning" in lower -> "Warning"
            "weak warning" in lower -> "Weak Warning"
            else -> "Problem"
        }
    }

    private fun inferMessage(text: String): String {
        return text.substringAfter(": ", text).trim()
    }
}
