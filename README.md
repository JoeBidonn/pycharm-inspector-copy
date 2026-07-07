# Inspector Copy for LLM

PyCharm / IntelliJ plugin that copies selected inspection errors from the **Problems** panel or **Inspection Results** tree as a structured Markdown block, ready to paste into any LLM chat.

## Output format

````markdown
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
````

## Usage

1. Open **Problems** (`Alt+6`) or run **Analyze → Inspect Code**
2. Select one or more problem nodes
3. Right-click → **Copy for LLM** or press `Ctrl+Alt+Shift+C`
4. Paste into ChatGPT / Claude / Copilot Chat

## Build & run locally

```bash
./gradlew runIde
./gradlew buildPlugin
./gradlew verifyPlugin
```

Requires JDK 21+.

## Roadmap

- [ ] Include inspection tool ID in output
- [ ] Add toolbar button for copy-all mode
- [ ] Configurable context lines
- [ ] JetBrains Marketplace publish
