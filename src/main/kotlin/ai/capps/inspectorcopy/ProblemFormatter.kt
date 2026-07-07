package ai.capps.inspectorcopy

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal data class ProblemEntry(
    val filePath: String,
    val lineNumber: Int,
    val severity: String,
    val message: String,
    val snippet: String,
)

internal object ProblemFormatter {
    fun format(entries: List<ProblemEntry>): String {
        if (entries.isEmpty()) {
            return "## Inspection Problems\n\n_No problems selected._\n"
        }

        return buildString {
            appendLine("## Inspection Problems")
            appendLine()

            entries.forEachIndexed { index, entry ->
                appendLine("### `${entry.filePath}:${entry.lineNumber}` — ${entry.severity}")
                appendLine(entry.message.trim())
                appendLine()
                appendLine("```python")
                append(entry.snippet.trimEnd())
                appendLine()
                appendLine("```")
                if (index != entries.lastIndex) {
                    appendLine()
                }
            }
        }
    }

    fun buildSnippet(
        project: Project?,
        file: VirtualFile?,
        lineNumber: Int,
        contextLines: Int = 5,
    ): String {
        if (file == null) return "<source unavailable>"
        val text = try {
            String(file.contentsToByteArray(), file.charset)
        } catch (_: Throwable) {
            return "<source unavailable>"
        }

        val lines = text.split("\n")
        if (lines.isEmpty()) return "<source unavailable>"

        val targetIndex = (lineNumber - 1).coerceIn(0, lines.lastIndex)
        val start = (targetIndex - contextLines).coerceAtLeast(0)
        val end = (targetIndex + contextLines).coerceAtMost(lines.lastIndex)
        val width = (end + 1).toString().length

        return buildString {
            for (index in start..end) {
                val marker = if (index == targetIndex) ">" else " "
                append(marker)
                append((index + 1).toString().padStart(width))
                append(": ")
                appendLine(lines[index].replace("\t", "    "))
            }
        }
    }

    fun relativize(project: Project?, file: VirtualFile?): String {
        if (file == null) return "<unknown file>"
        val basePath = project?.basePath
        val filePath = file.path
        return if (basePath != null && filePath.startsWith(basePath)) {
            filePath.removePrefix(basePath).trimStart('/', '\\')
        } else {
            file.name
        }
    }
}
