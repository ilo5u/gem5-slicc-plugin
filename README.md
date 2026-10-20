# SLICC / gem5 Ruby Language Plugin for CLion / IntelliJ IDEA

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

Language support for `.sm` and `.slicc` files used in the
[gem5](https://www.gem5.org) Ruby memory-subsystem cache coherence framework.

See [CHANGELOG.md](CHANGELOG.md) for the upcoming 1.2.0 release notes draft.

Marketplace description, features, and 1.2.0 What's New are available in [MARKETPLACE.html](MARKETPLACE.html).
The standalone Markdown description is available in [DESCRIPTION.md](DESCRIPTION.md).
The HTML onboarding guide is available in [GET_STARTED.html](GET_STARTED.html).

The product description and 1.2.0 release notes are also synchronized into
`src/main/resources/META-INF/plugin.xml` (`<description>` and `<change-notes>`).
Keep these copies aligned when editing the Marketplace HTML. Marketplace normally
uses the uploaded plugin's metadata; if you have selected a permanent page-level
description override, change that setting to use `plugin.xml` again. See the
[official listing guidance](https://plugins.jetbrains.com/docs/marketplace/best-practices-for-listing.html#plugin-description).

---

## Implemented Features

| Feature | Detail |
|---------|--------|
| **Syntax highlighting** | Declaration keywords (`machine`, `structure`, `action`, `transition`, …), control flow, type keywords, built-in SLICC statements (`peek`, `enqueue`, `dequeue`, `trigger`, `stall_and_wait`, `new`, `isReady`, …), attribute keywords, comments, strings, numbers |
| **Semantic highlighting** | Machine / structure / enumeration names highlighted as class names; action / function names as function declarations; enum members as constants; structure fields as instance fields; implicit variables (`tbe`, `cache_entry`, `address`) as predefined symbols |
| **Go-to-Declaration** | `Ctrl+Click` (or `Ctrl+B`) on any identifier jumps to its definition, with block-scoped variables, parameter/local shadowing, and overload-aware resolution for functions |
| **Action → transitions** | `Ctrl+Click` / `Ctrl+B` on an action declaration name jumps to its sole referencing transition, or opens a chooser showing transition headers and files when there are multiple matches |
| **Function navigation** | Calls and separate declarations jump to the matching definition; clicking a definition name, or a declaration with no SLICC definition (including structure/external-type methods), lists concrete calling statements in functions/actions and navigates to the call's line. One statement jumps directly; multiple statements open a chooser with file/line locations. Multiline statements display on one line, capped at 160 characters with an ellipsis; override using the IDE VM option `-Dslicc.navigation.maxStatementLength=240`. Match parameter signatures and machine/include/type scopes; calls to declaration-only functions still navigate to the declaration |
| **Go to Symbol** | `Ctrl+Alt+Shift+N` to search for any action, function, machine, or type by name |
| **Find Usages** | `Alt+F7` on any named declaration finds its reference sites, distinguishing same-named fields, local variables, and resolved function overloads |
| **Auto-completion** | All SLICC keywords, gem5 built-in types, file-local symbols, and free functions visible through includes, protocol files, and global declarations. Function candidates show return types and parameter signatures; dot-triggered member completion (`tbe.`, `in_msg.`, `someVar.`) shows fields and functions of the resolved type |
| **Parameter info** | `Ctrl+P` (or auto-popup on `(`) shows all overloads of a function with parameter types; current argument position is highlighted |
| **Scoped message variables** | `in_msg` is available only inside `peek` bodies and `out_msg` only inside `enqueue` bodies, including nested blocks. Their types come from the statement's second argument and drive member completion and navigation; usage outside the corresponding scope is reported as an error |
| **Member access with comments** | Member completion and navigation support whitespace and comments on either side of `.`, including chained accesses |
| **Unresolved identifier errors** | Identifiers that cannot be resolved to any declaration are underlined in red |
| **Structure View** | `Alt+7` shows a tree: machine → in_ports, out_ports, actions, transitions, functions |
| **Code Folding** | `machine`, `in_port`, `action`, and free-standing function bodies can be collapsed |
| **Brace Matching** | Auto-pair and highlight matching `{ }`, `( )`, `[ ]` |
| **Comment Toggle** | `Ctrl+/` for line comment, `Ctrl+Shift+/` for block comment |
| **Colour Customisation** | Settings → Editor → Color Scheme → **SLICC (gem5 Ruby)** |
| **Cross-file resolution** | Identifiers resolve across all files in the same protocol directory, plus global declarations in `RubySlicc_interfaces.slicc` |

## Not Yet Implemented

- **Rename refactoring** (`Shift+F6`) — `SliccNamedElement.setName()` is not implemented; renaming a symbol does not update all usages.
- **Code formatter** — No `FormattingModelBuilder`; auto-indent and reformat (`Ctrl+Alt+L`) are not available.
- **Type-aware member error checking** — Dot-access RHS (e.g. `tbe.nonexistent`) is not checked; only top-level identifier resolution is validated.
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

### Windows: backend exits with code 77

If `runIde` reports that the Rider host exited with code 77, inspect
`backend.<timestamp>.<pid>.log` under
`.intellijPlatform/sandbox/slicc-plugin/<IDE-version>/log/` for the underlying error.
CLion also uses this backend, so the message can appear while running CLion.

If the log reports `COR_E_PATHTOOLONG`, `0x800700CE`, or "The filename or extension
is too long" while loading a DLL, use `ide.localPath` above to select an installed
IDE in a shorter path. An IDE extracted deep inside the Gradle cache can exceed
Windows path limits, especially with a long `GRADLE_USER_HOME` such as a Scoop
installation directory. Alternatively, use a shorter Gradle user home before
starting Gradle; this creates a separate cache and may download dependencies again.
An existing cached IDE can also be exposed through a shorter Windows directory
junction, with `ide.localPath` pointing to the junction. Keep its target intact;
clearing that Gradle cache requires recreating the junction.

The `Debug privileges not enabled` message alone does not identify the cause of
the backend exit. Verify the fix by restarting `runIde` and checking the new
backend log. Keep local paths in the gitignored `gradle.properties` file, and
check plugin compatibility separately against the oldest IDE version you support
if your local installation is newer.

If Gradle cannot find the Java toolchain required by that IDE, point
`org.gradle.java.installations.paths` in `gradle.properties` to its bundled
`jbr` directory (provided it contains `bin/javac.exe`), or install a matching JDK.

### Build commands

```bash
# Build the distributable plugin ZIP
./gradlew buildPlugin
# Output: build/distributions/slicc-plugin-1.2.0.zip

# Launch a sandboxed IDE with the plugin loaded (primary way to test)
./gradlew runIde
```

---

## Installing in CLion / IntelliJ IDEA

1. Go to **Settings → Plugins → ⚙ → Install Plugin from Disk…**
2. Select `build/distributions/slicc-plugin-1.2.0.zip`
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
    │   │   ├── SliccAnnotator.java         # Semantic highlighting + unresolved identifier errors
    │   │   └── SliccFoldingBuilder.java
    │   ├── completion/
    │   │   ├── SliccCompletionContributor.java  # Keyword, symbol, dot-member completion
    │   │   ├── SliccParameterInfoHandler.java   # Ctrl+P parameter info popup
    │   │   └── SliccTypedHandler.java           # Auto-popup on '('
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
