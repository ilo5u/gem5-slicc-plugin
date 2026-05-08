# SLICC / gem5 Ruby Language Plugin for CLion / IntelliJ IDEA

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

Language support for `.sm` and `.slicc` files used in the
[gem5](https://www.gem5.org) Ruby memory-subsystem cache coherence framework.

---

## Implemented Features

| Feature | Detail |
|---------|--------|
| **Syntax highlighting** | Declaration keywords (`machine`, `structure`, `action`, `transition`, …), control flow, type keywords, built-in SLICC statements (`peek`, `enqueue`, `dequeue`, `trigger`, `stall_and_wait`, `new`, `isReady`, …), attribute keywords, comments, strings, numbers |
| **Semantic highlighting** | Machine / structure / enumeration names highlighted as class names; action / function names as function declarations; enum members as constants; structure fields as instance fields; implicit variables (`tbe`, `cache_entry`, `address`) as predefined symbols |
| **Go-to-Declaration** | `Ctrl+Click` (or `Ctrl+B`) on any identifier jumps to its definition, with overload-aware resolution for functions |
| **Go to Symbol** | `Ctrl+Alt+Shift+N` to search for any action, function, machine, or type by name |
| **Find Usages** | `Alt+F7` on any named declaration finds all reference sites |
| **Auto-completion** | All SLICC keywords, gem5 built-in types, and file-local symbols (actions, functions, structures, enumerations) |
| **Structure View** | `Alt+7` shows a tree: machine → in_ports, out_ports, actions, transitions, functions |
| **Code Folding** | `machine`, `in_port`, `action`, and free-standing function bodies can be collapsed |
| **Brace Matching** | Auto-pair and highlight matching `{ }`, `( )`, `[ ]` |
| **Comment Toggle** | `Ctrl+/` for line comment, `Ctrl+Shift+/` for block comment |
| **Colour Customisation** | Settings → Editor → Color Scheme → **SLICC (gem5 Ruby)** |
| **Cross-file resolution** | Identifiers resolve across all files in the same protocol directory, plus global declarations in `RubySlicc_interfaces.slicc` |

## Not Yet Implemented

- **Rename refactoring** (`Shift+F6`) — `SliccNamedElement.setName()` is not implemented; renaming a symbol does not update all usages.
- **Code formatter** — No `FormattingModelBuilder`; auto-indent and reformat (`Ctrl+Alt+L`) are not available.
- **Error / warning annotations** — The plugin does not perform type checking or report undefined references as errors.
- **Inlay hints** — Parameter name hints and type hints are not shown.
- **Live templates** — No built-in code snippets for common SLICC patterns.

---

## Building

### Prerequisites

- JDK 21+
- `gradle/wrapper/gradle-wrapper.jar` (not committed — see [`GET_WRAPPER_JAR.md`](GET_WRAPPER_JAR.md))
- Internet access on first build (Gradle downloads the IntelliJ SDK automatically)

### Optional: use a local IDE installation

By default, CLion 2026.1 is downloaded automatically. To use a locally installed
IDE instead (faster builds), add the following to `gradle.properties` in the
project root (this file is gitignored — do not commit it):

```properties
ide.localPath=/path/to/CLion
# Windows example: ide.localPath=C:/Program Files/JetBrains/CLion 2026.1
# macOS example:   ide.localPath=/Applications/CLion.app/Contents
```

### Build commands

```bash
# Build the distributable plugin ZIP
./gradlew buildPlugin
# Output: build/distributions/slicc-plugin-1.0.1.zip

# Launch a sandboxed IDE with the plugin loaded (primary way to test)
./gradlew runIde
```

---

## Installing in CLion / IntelliJ IDEA

1. Go to **Settings → Plugins → ⚙ → Install Plugin from Disk…**
2. Select `build/distributions/slicc-plugin-1.0.1.zip`
3. Restart the IDE
4. Open any `.sm` or `.slicc` file — highlighting activates immediately

---

## Project Structure

```
slicc-plugin/
├── build.gradle.kts
├── settings.gradle.kts
└── src/main/
    ├── java/com/slicc/plugin/
    │   ├── lang/
    │   │   ├── SliccLanguage.java          # Language singleton
    │   │   ├── SliccFileType.java          # .sm / .slicc file type
    │   │   ├── SliccCommenter.java         # // and /* */ comment support
    │   │   └── SliccBraceMatcher.java      # Brace auto-pairing
    │   ├── lexer/
    │   │   ├── SliccLexer.java             # Hand-written lexer
    │   │   ├── SliccTokenType.java         # IElementType wrapper
    │   │   └── SliccTokenTypes.java        # All token type constants
    │   ├── parser/
    │   │   ├── SliccParserDefinition.java  # Parser + comment/whitespace sets
    │   │   ├── SliccParser.java            # Recursive-descent parser
    │   │   └── SliccPsiElementFactory.java # PSI node factory
    │   ├── psi/
    │   │   ├── SliccFile.java              # Root PSI file node
    │   │   ├── SliccCompositeElement.java  # Base composite PSI node
    │   │   ├── SliccTypes.java             # All composite node type constants
    │   │   ├── SliccNamedElement.java      # Base for all named declarations
    │   │   ├── SliccMachineDecl.java
    │   │   ├── SliccStructureDecl.java
    │   │   ├── SliccEnumerationDecl.java
    │   │   ├── SliccStateDecl.java
    │   │   ├── SliccExternalTypeDecl.java
    │   │   ├── SliccActionDecl.java
    │   │   ├── SliccTransitionDecl.java
    │   │   ├── SliccFunctionDecl.java
    │   │   ├── SliccInPortDecl.java
    │   │   ├── SliccOutPortDecl.java
    │   │   ├── SliccFieldDecl.java
    │   │   ├── SliccEnumMember.java
    │   │   └── SliccIncludeDecl.java
    │   ├── highlighting/
    │   │   ├── SliccSyntaxHighlighter.java
    │   │   ├── SliccSyntaxHighlighterFactory.java
    │   │   ├── SliccColorSettingsPage.java
    │   │   ├── SliccAnnotator.java         # Semantic highlighting
    │   │   └── SliccFoldingBuilder.java
    │   ├── completion/
    │   │   └── SliccCompletionContributor.java
    │   ├── navigation/
    │   │   ├── SliccReference.java         # Resolves identifiers → declarations
    │   │   ├── SliccReferenceContributor.java
    │   │   ├── SliccFindUsagesProvider.java
    │   │   ├── SliccGotoDeclarationHandler.java
    │   │   ├── SliccGotoSymbolContributor.java
    │   │   ├── SliccProtocolIndex.java
    │   │   └── SliccStructureViewFactory.java
    │   └── icons/
    │       └── SliccIcons.java
    └── resources/
        ├── META-INF/plugin.xml
        └── icons/
```

---

## Contributing

Bug reports, feature requests, and pull requests are welcome.

- **Report a bug or request a feature**: [Open an issue](../../issues)
- **Known limitations**: See the "Not Yet Implemented" section above

When reporting a bug, please include:
- The IDE version (CLion / IntelliJ IDEA)
- A minimal `.sm` or `.slicc` snippet that reproduces the problem
- What you expected vs. what actually happened

---

## License

This project is licensed under the [Apache License 2.0](LICENSE).
