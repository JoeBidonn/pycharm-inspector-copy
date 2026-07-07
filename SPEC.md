# PyCharm Inspector Copy — Project Spec

## What this is

An IntelliJ Platform plugin (targets PyCharm, works in any JetBrains IDE) that copies
selected inspection errors from the IDE's Problems panel or Inspection Results tree as a
structured Markdown block, ready to paste into any LLM chat (Claude, ChatGPT, Copilot, etc.).

## Problem being solved

When you get an inspection error in PyCharm you currently have to:
1. Read the error in the panel
2. Navigate to the file
3. Manually copy the message + surrounding code
4. Paste and re-format it for the LLM

This plugin collapses that into one keypress.

## Output format

```markdown
## Inspection Problems

### `maptara_codexa/models/contract.py:142` — Error
Cannot resolve symbol 'many2one_link'

```python
 137:     _name = 'codexa.contract'
 138:     _inherit = ['mail.thread']
 139:
 140:     partner_id = fields.Many2one(
 141:         'res.partner',
>142:         many2one_link,
 143:     )
```
```

Fields per entry: relative file path, line number, severity label, inspection message, and ±5 lines of code with the problem line marked using `>`.

## Entry points

| Trigger | Where |
|---|---|
| `Ctrl+Alt+Shift+C` | anywhere, acts on focused tree |
| Right-click → **Copy for LLM** | Problems panel context menu |
| Right-click → **Copy for LLM** | Inspect Code results tree context menu |

## Two data source paths

### Path 1 — Problems panel (highlight-based)
- Tool window opened with `Alt+6`
- Shows current-file or project-wide highlighting errors
- Node type: `ProblemNode` wrapping a `HighlightingProblem`
- Line derived from `HighlightInfo.startOffset` → `Document.getLineNumber()`

### Path 2 — Inspect Code results tree (batch inspection run)
- Opened via Analyze → Inspect Code
- Node type: `ProblemDescriptionNode`
- Line derived from `PsiElement.textOffset` → `PsiDocumentManager.getDocument()`

The action should try Path 1 first and fall back to Path 2 if nothing is collected.

## Tech stack

- Kotlin 2.0
- Gradle + IntelliJ Platform Gradle Plugin v2
- Target IDE: PyCharm
- Min build: 233
- Max build: 251.*
- JDK: 21

## Repository layout

```text
pycharm-inspector-copy/
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── SPEC.md
├── README.md
└── src/main/
    ├── kotlin/ai/capps/inspectorcopy/
    │   ├── CopyForLlmAction.kt
    │   └── ProblemFormatter.kt
    └── resources/META-INF/
        └── plugin.xml
```

## Immediate next tasks

1. Run `gradle wrapper`
2. `./gradlew runIde`
3. Fix API compatibility issues found in sandbox testing
4. Add a toolbar button for copy-all mode
5. Make context line count configurable
