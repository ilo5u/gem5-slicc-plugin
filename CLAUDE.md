# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

**Prerequisites:**
- JDK 21+
- `gradle/wrapper/gradle-wrapper.jar` must exist (not committed — see `GET_WRAPPER_JAR.md` for setup)
- CLion 2026.1 installed locally (set `ide.localPath` in `gradle.properties` — see README)

```bash
# Build the plugin zip
./gradlew buildPlugin
# Output: build/distributions/slicc-plugin-<version>.zip

# Launch sandboxed IDE with plugin loaded (primary way to test)
./gradlew runIde
```

There is no automated test suite (`src/test/` is absent). Test manually using `./gradlew runIde` with the sample SLICC files in `ruby/` (MI, MESI, MOESI, GPU_VIPER, Garnet protocols from gem5).

## Architecture

This is an IntelliJ Platform plugin (SDK `org.jetbrains.intellij.platform` 2.16.0, targets CLion 2026.1+) providing language support for the gem5 Ruby **SLICC** language (`.sm` / `.slicc` files).

All source lives under `src/main/java/com/slicc/plugin/`.

### Component Pipeline

```
Source file → Lexer → Parser → PSI Tree → Annotator / Reference / Completion
```

1. **Lexer** (`lexer/SliccLexer.java`) — Hand-written lexer; tokenizes 40+ keywords, C++-style comments, `:=` assignments, and SLICC-specific keywords (`machine`, `action`, `transition`, `peek`, `enqueue`, …).

2. **Parser** (`parser/SliccParser.java`) — Recursive-descent parser that builds the PSI tree. Top-level grammar handles: `machine`, `structure`, `enumeration`, `state_declaration`, `external_type`, `include`, and function declarations.

3. **PSI layer** (`psi/`) — Each declaration type has its own class extending `SliccNamedElement` (which enables rename/navigation). Key nodes: `SliccMachineDecl`, `SliccStructureDecl`, `SliccEnumerationDecl`, `SliccStateDecl`, `SliccActionDecl`, `SliccTransitionDecl`, `SliccFunctionDecl`, `SliccIn/OutPortDecl`, `SliccFieldDecl`, `SliccEnumMember`, `SliccIncludeDecl`.

4. **Highlighting** (`highlighting/`)
   - `SliccSyntaxHighlighter` — lexical/keyword colors
   - `SliccAnnotator` — semantic colors (machine names → class color, enum names → interface color, resolved type refs → colored); also reports **unresolved identifier errors** (red underline) for identifiers that cannot be resolved. False-positive guards: dot-access RHS, C++ scoped names (`std::string`), DPRINTF first arg, auto-generated machine symbols (`{MachineName}_*`), implicit vars (`tbe`, `cache_entry`, `address`, `in_msg`, `out_msg`).
   - `SliccFoldingBuilder` — code folding for machine/in_port/action/function bodies

5. **Navigation & Resolution** (`navigation/`)
   - `SliccReference` — resolves identifiers to declarations; searches current file → protocol-group → global scope
   - `SliccGotoDeclarationHandler` — Ctrl+Click / Ctrl+B; overload-aware (matches call-site argument count to function parameter count)
   - `SliccProtocolIndex` — groups `.sm`/`.slicc` files by protocol directory and exposes global symbols (including via `RubySlicc_interfaces.slicc`)
   - `SliccGotoSymbolContributor` — Ctrl+Alt+Shift+N symbol search
   - `SliccStructureViewFactory` — Alt+7 structure tree (machine → in_ports, out_ports, actions, transitions, functions)

6. **Completion** (`completion/`)
   - `SliccCompletionContributor` — Keyword completions + symbol completions (file-local actions/functions/structures + gem5 built-in types). Dot-triggered member completion: when the cursor is after `.`, resolves the owner's type and shows only its fields and member functions (suppresses keyword/symbol completions). Action names are suppressed inside action bodies.
   - `SliccParameterInfoHandler` — `Ctrl+P` parameter info popup; shows all overloads with parameter types, highlights the current argument. Registered as `codeInsight.parameterInfo` (not `lang.parameterInfo`).
   - `SliccTypedHandler` — Auto-triggers the parameter info popup when `(` is typed, via `invokeLater` + `PsiDocumentManager.commitDocument`.

### Cross-File Resolution Design

Three-tier scope resolution in `SliccReference.resolve()`:
1. **Current file** — searched first, no index needed.
2. **Protocol-group scope** (`SliccProtocolIndex.groupContaining`) — files reachable from the same `.slicc` root via `include` directives.
3. **Global scope** (`SliccProtocolIndex.globalFiles`) — files reachable from `RubySlicc_interfaces.slicc`.

`SliccProtocolIndex` uses `CachedValue` keyed per-file, invalidated on `PsiModificationTracker` changes. Protocol groups are built by following `include` chains from `.slicc` root files.

`SliccReference.resolve()` searches type declarations first when the identifier is in a `TYPE_REF` position, then all declarations.

### Registration

All extensions are registered in `src/main/resources/META-INF/plugin.xml` (fileType, parser definition, syntax highlighter, annotator, folding builder, completion contributor, parameter info handler, typed handler, reference contributor, structure view, goto symbol contributor, color settings page).

## Key Extension Points (Not Yet Implemented)

- **Rename refactoring**: `SliccNamedElement.setName()` needs implementation.
- **Code formatting**: Needs a `FormattingModelBuilder`.
- **Type-aware member error checking**: Dot-access RHS (e.g. `tbe.nonexistent`) is not validated; only top-level identifier resolution is checked.
