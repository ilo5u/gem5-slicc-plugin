# SLICC / gem5 Ruby Language Plugin for CLion / IntelliJ IDEA

Language support for `.sm` and `.slicc` files used in the
[gem5](https://www.gem5.org) Ruby memory-subsystem cache coherence framework.

---

## Features

| Feature | Detail |
|---------|--------|
| **Syntax highlighting** | Declaration keywords (`machine`, `structure`, `action`, `transition`, …), control flow, type keywords, built-in SLICC statements (`peek`, `enqueue`, `trigger`, `stall_and_wait`, …), attribute keywords, comments, strings, numbers |
| **Semantic highlighting** | Machine / structure / enumeration names highlighted as class names; action / function names as function declarations |
| **Go-to-Declaration** | `Ctrl+Click` (or `Ctrl+B`) on any identifier jumps to its definition |
| **Find Usages** | `Alt+F7` on any named declaration finds all reference sites |
| **Go to Symbol** | `Ctrl+Alt+Shift+N` to search for any action, function, machine, or type by name |
| **Auto-completion** | All SLICC keywords, gem5 built-in types, and file-local symbols (actions, functions, structures, enumerations) |
| **Structure View** | `Alt+7` shows a tree: machine → in_ports, out_ports, actions, transitions, functions |
| **Code Folding** | `machine`, `in_port`, `action`, and free-standing function bodies can be collapsed |
| **Brace Matching** | Auto-pair and highlight matching `{ }`, `( )`, `[ ]` |
| **Comment Toggle** | `Ctrl+/` for line comment, `Ctrl+Shift+/` for block comment |
| **Colour Customisation** | Settings → Editor → Color Scheme → **SLICC (gem5 Ruby)** |

---

## Building

### Prerequisites

- JDK 21+
- Gradle wrapper (see `GET_WRAPPER_JAR.md` for setup before first build)
- Internet access (Gradle downloads the IntelliJ SDK on first build)

```bash
# Clone / place the plugin sources, then:
cd slicc-plugin
./gradlew buildPlugin
```

The distributable ZIP will be at:
```
build/distributions/slicc-plugin-1.0.1.zip
```

### Running in a sandbox IDE (for testing)

```bash
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
    │   │   ├── SliccElementType.java       # Element type wrapper
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
    │   │   ├── SliccGotoSymbolContributor.java
    │   │   └── SliccStructureViewFactory.java
    │   └── icons/
    │       └── SliccIcons.java
    └── resources/
        ├── META-INF/plugin.xml
        └── icons/
            ├── slicc_file.svg
            ├── slicc_machine.svg
            ├── slicc_structure.svg
            ├── slicc_action.svg
            ├── slicc_transition.svg
            ├── slicc_function.svg
            └── slicc_enum.svg
```

---

## Extending the Plugin

### Adding rename refactoring

Override `SliccNamedElement.setName()` to use `PsiElement.replace()` with a
freshly-created leaf node containing the new name.

### Adding a formatter

Implement `com.intellij.lang.formatter.FormattingModelBuilder` and register it
in `plugin.xml` as `<lang.formatter language="SLICC" …/>`.

---

## Tested With

- CLion 2026.1
- gem5 SLICC files from Ruby (MSI, MESI, MOESI, custom protocols)
- JDK 21
