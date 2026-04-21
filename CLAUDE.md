# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

**Prerequisites:**
- JDK 21+
- `gradle/wrapper/gradle-wrapper.jar` must exist (not committed — see `GET_WRAPPER_JAR.md` for setup)
- CLion 2026.1 installed locally (configured at `C:/Users/chengxin/AppData/Local/Programs/CLion`)

```bash
# Build the plugin zip
./gradlew buildPlugin
# Output: build/distributions/slicc-plugin-1.0.1.zip

# Launch sandboxed IDE with plugin loaded (primary way to test)
./gradlew runIde
```

There is no automated test suite (`src/test/` is absent). Test manually using real gem5 Ruby SLICC files (MSI, MESI, MOESI protocols).

## Architecture

This is an IntelliJ Platform plugin providing language support for the gem5 Ruby **SLICC** language (`.sm` / `.slicc` files).

### Component Pipeline

```
Source file → Lexer → Parser → PSI Tree → Annotator / Reference / Completion
```

1. **Lexer** (`lexer/SliccLexer.java`) — Hand-written lexer; tokenizes 40+ keywords, C++-style comments, `:=` assignments, and SLICC-specific keywords (`machine`, `action`, `transition`, `peek`, `enqueue`, …).

2. **Parser** (`parser/SliccParser.java`) — Recursive-descent parser that builds the PSI tree. Top-level grammar handles: `machine`, `structure`, `enumeration`, `state_declaration`, `external_type`, `include`, and function declarations.

3. **PSI layer** (`psi/`) — Each declaration type has its own class extending `SliccNamedElement` (which enables rename/navigation). Key nodes: `SliccMachineDecl`, `SliccStructureDecl`, `SliccEnumerationDecl`, `SliccStateDecl`, `SliccActionDecl`, `SliccTransitionDecl`, `SliccFunctionDecl`, `SliccIn/OutPortDecl`, `SliccFieldDecl`, `SliccEnumMember`, `SliccIncludeDecl`.

4. **Highlighting** (`highlighting/`)
   - `SliccSyntaxHighlighter` — lexical/keyword colors
   - `SliccAnnotator` — semantic colors (machine names → class color, enum names → interface color, resolved type refs → colored)
   - `SliccFoldingBuilder` — code folding for machine/in_port/action/function bodies

5. **Navigation & Resolution** (`navigation/`)
   - `SliccReference` — resolves identifiers to declarations; searches current file then protocol-group scope
   - `SliccProtocolIndex` — groups `.sm`/`.slicc` files by protocol directory and exposes global symbols (including via `RubySlicc_interfaces.slicc`)
   - `SliccGotoSymbolContributor` — Ctrl+Alt+Shift+N symbol search
   - `SliccStructureViewFactory` — Alt+7 structure tree (machine → in_ports, out_ports, actions, transitions, functions)

6. **Completion** (`completion/SliccCompletionContributor.java`) — Keyword completions + symbol completions (file-local actions/functions/structures + gem5 built-in types: `Addr`, `DataBlock`, `MachineID`, …).

### Cross-File Resolution Design

Two-tier scope resolution in `SliccProtocolIndex`:
- **Protocol-group scope**: files sharing the same directory are treated as one protocol unit.
- **Global scope**: `RubySlicc_interfaces.slicc` provides globally visible built-in declarations.

`SliccReference.resolve()` searches type declarations first for `TYPE_REF` positions, then all declarations.

### Registration

All extensions are registered in `src/main/resources/META-INF/plugin.xml` (fileType, parser definition, syntax highlighter, annotator, folding builder, completion contributor, reference contributor, structure view, goto symbol contributor, color settings page).

## Key Extension Points (Not Yet Implemented)

- **Rename refactoring**: `SliccNamedElement.setName()` needs implementation.
- **Code formatting**: Needs a `FormattingModelBuilder`.
