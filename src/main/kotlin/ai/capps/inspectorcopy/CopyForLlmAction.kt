package ai.capps.inspectorcopy

import com.intellij.ide.CopyPasteManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
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
        val tree = focusedTree()
        if (tree == null) {
            Messages.showInfoMessage(
                project,
                "Focus the Problems or Inspection Results tree and try again.",
                "Copy for LLM",
            )
            return
        }

        val fallbackFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
            ?: FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
            ?: project.guessProjectDir()

        val entries = collectFromTree(project, tree, fallbackFile)
            .distinctBy { Triple(it.filePath, it.lineNumber, it.message) }

        if (entries.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "No supported problem nodes were found in the current selection.",
                "Copy for LLM",
            )
            return
        }

        val markdown = ProblemFormatter.format(entries)
        CopyPasteManager.getInstance().setContents(StringSelection(markdown))
        Messages.showInfoMessage(
            project,
            "Copied ${entries.size} problem(s) as Markdown.",
            "Copy for LLM",
        )
    }

    private fun focusedTree(): JTree? =
        KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner as? JTree

    private fun collectFromTree(
        project: Project,
        tree: JTree,
        fallbackFile: VirtualFile?,
    ): List<ProblemEntry> {
        val selectionPaths = tree.selectionPaths?.toList().orEmpty()
        return selectionPaths.mapNotNull { collectEntry(project, it, fallbackFile) }
    }

    private fun collectEntry(
        project: Project,
        path: TreePath,
        fallbackFile: VirtualFile?,
    ): ProblemEntry? {
        val node = path.lastPathComponent ?: return null
        val userObject = extractUserObject(node)
        val text = node.toString().trim().ifBlank { return null }

        val file = inferFile(project, userObject) ?: fallbackFile
        val line = inferLine(userObject, text)
        val severity = inferSeverity(text)
        val message = inferMessage(text)

        return ProblemEntry(
            filePath = ProblemFormatter.relativize(project, file),
            lineNumber = line,
            severity = severity,
            message = message,
            snippet = ProblemFormatter.buildSnippet(file, line),
        )
    }

    private fun extractUserObject(node: Any): Any? =
        runCatching {
            val method = node.javaClass.methods.firstOrNull {
                it.name == "getUserObject" && it.parameterCount == 0
            }
            method?.invoke(node)
        }.getOrNull()

    private fun inferFile(
        project: Project,
        source: Any?,
    ): VirtualFile? {
        if (source == null) {
            return FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        }

        extractVirtualFile(source)?.let { return it }
        extractPsiFileVirtualFile(source)?.let { return it }
        extractContainingFileVirtualFile(source)?.let { return it }

        return FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
    }

    private fun extractVirtualFile(value: Any): VirtualFile? {
        if (value is VirtualFile) return value
        return runCatching {
            val method = value.javaClass.methods.firstOrNull {
                it.name == "getVirtualFile" && it.parameterCount == 0
            }
            method?.invoke(value) as? VirtualFile
        }.getOrNull()
    }

    private fun extractPsiFileVirtualFile(value: Any): VirtualFile? =
        runCatching {
            val psiFileMethod = value.javaClass.methods.firstOrNull {
                it.name == "getPsiFile" && it.parameterCount == 0
            }
            val psiFile = psiFileMethod?.invoke(value) ?: return null
            val vfMethod = psiFile.javaClass.methods.firstOrNull {
                it.name == "getVirtualFile" && it.parameterCount == 0
            }
            vfMethod?.invoke(psiFile) as? VirtualFile
        }.getOrNull()

    private fun extractContainingFileVirtualFile(value: Any): VirtualFile? =
        runCatching {
            val containingFileMethod = value.javaClass.methods.firstOrNull {
                it.name == "getContainingFile" && it.parameterCount == 0
            }
            val containingFile = containingFileMethod?.invoke(value) ?: return null
            val vfMethod = containingFile.javaClass.methods.firstOrNull {
                it.name == "getVirtualFile" && it.parameterCount == 0
            }
            vfMethod?.invoke(containingFile) as? VirtualFile
        }.getOrNull()

    private fun inferLine(
        source: Any?,
        text: String,
    ): Int {
        val reflectedLine = source?.let { obj ->
            sequenceOf("getLine", "getLineNumber")
                .mapNotNull { methodName ->
                    runCatching {
                        val method = obj.javaClass.methods.firstOrNull {
                            it.name == methodName && it.parameterCount == 0
                        }
                        (method?.invoke(obj) as? Number)?.toInt()
                    }.getOrNull()
                }
                .firstOrNull { it > 0 }
        }
        if (reflectedLine != null) return reflectedLine

        val regex = Regex("(?:^|[^0-9])(\\d{1,6})(?:[^0-9]|$)")
        return regex.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
    }

    private fun inferSeverity(text: String): String {
        val lower = text.lowercase()
        return when {
            "weak warning" in lower -> "Weak Warning"
            "warning" in lower -> "Warning"
            "error" in lower -> "Error"
            else -> "Problem"
        }
    }

    private fun inferMessage(text: String): String =
        text.substringAfter(": ", text).trim()
}
