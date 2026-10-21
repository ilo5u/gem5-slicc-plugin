# AGENTS.md

This file provides guidance to coding agents working in this repository. It applies to the entire repository unless a more specific AGENTS.md overrides it.

## Build Commands

**Prerequisites:**
- JDK 21+
- `gradle/wrapper/gradle-wrapper.jar` must exist (not committed — see `GET_WRAPPER_JAR.md` for setup)
- CLion 2026.1 is downloaded automatically unless `ide.localPath` in `gradle.properties` points to a local IDE installation (see README). Keep this local configuration uncommitted.

```bash
# Build the plugin zip
./gradlew buildPlugin
# Output: build/distributions/slicc-plugin-1.3.0.zip
# The version is defined in build.gradle.kts.

# Launch sandboxed IDE with plugin loaded (primary way to test)
./gradlew runIde
```

On Windows PowerShell, use `.\gradlew.bat buildPlugin` and `.\gradlew.bat runIde`.

There is no automated test suite (`src/test/` is absent). Test manually using real gem5 Ruby SLICC files (MSI, MESI, MOESI protocols).

## Release Tracking

The current release version is **1.3.0**, prepared on **2026-10-21**. Its changes
are recorded in the dated section of `CHANGELOG.md`. Record subsequent changes in
a new unreleased section rather than changing the released feature list. Keep the
user-facing notes in `MARKETPLACE.html` synchronized with
the `<change-notes>` section of `src/main/resources/META-INF/plugin.xml`, preserving
previous release entries. The 1.3.0 user-facing release notes are synchronized.
Keep the Marketplace short-description paragraph and Description/Features/Current
Limitations sections synchronized with `<description>` too; do not include What's
New there. Keep `DESCRIPTION.md`, the standalone Markdown version of the product
description, aligned with the same content. `GET_STARTED.html` is the separate
Marketplace onboarding guide.

## Architecture

This is an IntelliJ Platform plugin providing language support for the gem5 Ruby **SLICC** language (`.sm` / `.slicc` files).

The component paths below are relative to `src/main/java/com/slicc/plugin/`. Gradle also includes `src/main/gen` as a Java source directory.

### Component Pipeline

```
Source file → Lexer → Parser → PSI Tree → Annotator / Reference / Completion
```

1. **Lexer** (`lexer/SliccLexer.java`) — Hand-written lexer; tokenizes 40+ keywords, C++-style comments, `:=` assignments, and SLICC-specific keywords (`machine`, `action`, `transition`, `peek`, `enqueue`, …).

2. **Parser** (`parser/SliccParser.java`) — Recursive-descent parser that builds the PSI tree. Top-level grammar handles: `machine`, `structure`, `enumeration`, `state_declaration`, `external_type`, `include`, and function declarations.

3. **PSI layer** (`psi/`) — Each declaration type has its own class extending `SliccNamedElement` (which enables rename/navigation). Key nodes: `SliccMachineDecl`, `SliccStructureDecl`, `SliccEnumerationDecl`, `SliccStateDecl`, `SliccActionDecl`, `SliccTransitionDecl`, `SliccFunctionDecl`, `SliccIn/OutPortDecl`, `SliccFieldDecl`, `SliccEnumMember`, `SliccIncludeDecl`.

4. **Highlighting** (`highlighting/`)
   - `SliccSyntaxHighlighter` — lexical/keyword colors
   - `SliccAnnotator` — semantic colors (machine names → class color, enum names → interface color, resolved type refs → colored); also reports **unresolved identifier errors** (red underline) for identifiers that cannot be resolved. Type-aware dot-RHS diagnostics check unknown field/method names against a uniquely resolved receiver's visible SLICC declaration and complete `interface` hierarchy, including external declarations, rather than unrestricted name lookup or C++ member inspection. Respect lexical shadowing, scoped message types, reference/pointer parameters, and machine/include/type ownership. Unknown types, missing/cyclic interfaces, ambiguous types, malformed declarations, indexing, and unsupported receiver expressions defer this name-only check; an unresolved chain element must not cause downstream cascades. Other false-positive guards include C++ scoped names (`std::string`), DPRINTF first arg, auto-generated machine symbols (`{MachineName}_*`), and action-scoped implicit vars (`tbe`, `cache_entry`, `address`). Message variables are highlighted only in their corresponding scope; out-of-scope usage is an error.
   - `SliccFoldingBuilder` — code folding for machine/in_port/action/function bodies

5. **Navigation & Resolution** (`navigation/`)
   - `SliccReference` — delegates resolution to the shared `SliccGotoDeclarationHandler` resolver, preserving block scope, local/parameter shadowing, member ownership, and overload selection. Find Usages compares resolved PSI declarations rather than names and declaration kinds alone.
   - `SliccMessageScope` — shares message-variable visibility and type inference across completion, highlighting, reference resolution, and navigation. Explicit function parameters named `in_msg` or `out_msg` take priority throughout the function, including nested `peek` / `enqueue` bodies, and use their declared types. Without a matching parameter, implicit `in_msg` belongs to a `peek` body and `out_msg` to an `enqueue` body; each takes its type from the second argument of the nearest matching enclosing statement. Nested blocks inherit the surrounding implicit bindings.
   - `SliccActionNavigation` — resolves action references in transition action lists within machine/include scope and collects referencing transitions, including all machine roots that include a shared action file via `SliccProtocolIndex.allMachineFilesContaining`. The UI goto handler reverses action-declaration navigation into one or more transition targets, while semantic references still resolve to the action declaration itself. Both transition body forms use `TRANSITION_ACTIONS` PSI nodes.
   - `SliccFunctionNavigation` — connects prototypes, definitions, and calls by parameter-type signature and machine/include/type ownership. Calls prefer matching definitions across files; a definition name, or a declaration without any matching SLICC definition, reverses UI navigation to concrete calling statements in functions/actions without changing its semantic self-reference. `findCallSites` deduplicates by enclosing statement, not by caller. `SliccUsageSite` uses a smart PSI pointer to present complete multiline statements on one line with file/line context and navigate to the actual call offset. Labels default to 160 Unicode code points including an ellipsis; the IDE VM property `slicc.navigation.maxStatementLength` overrides the limit (minimum 1). Declarations with definitions still navigate to those definitions, and calls to declaration-only functions still navigate to their declarations. Shared prototypes are resolved in the caller's machine context; member and free-function namespaces remain separate. Scope data is cached per file against PSI modifications. Find Usages recognizes a prototype and definition as the same function, but not unrelated same-named overloads or machine/type members.
   - `SliccProtocolIndex` — shares a project-level protocol/machine/include graph with reverse file-to-scope lookups, including globals via `RubySlicc_interfaces.slicc`. File-level include nodes are cached independently; project graph dependencies cover PSI, VFS structure, and project-root changes. Preserve first matching protocol/machine behavior and all-machine unions for reverse navigation.
   - `SliccFileSymbols` — caches declaration lists by name and declaration class against the containing PSI file, preserving source order. Reuse it in resolution and highlighting instead of repeatedly walking every file for each identifier.
   - `SliccExpressionTypes` — Conservative scope-aware expression model for function-result diagnostics. A `Session` infers supported simple variables, fields, free/member/scoped calls, nested calls, parentheses, and supported literals. Select overload candidates by argument compatibility, never the assignment target's desired return type. Compare nominal SLICC base types and supported `interface` relationships; reference/pointer call qualifiers are not C++ conversion rules, `primitive="yes"` does not make every external type numeric, and `external_name` does not merge distinct SLICC declarations. Unknown types/arguments, ambiguous owners/types, conflicting overloads, incomplete declarations, complex operators, indexed expressions, and indexing defer diagnostics rather than causing cascades. Reuse per-session resolution state and preserve cancellation; do not fall back to unrelated project-wide names.
   - `SliccTypeDiagnostics` — Reports definite mismatches for function-result assignments/initializers and function results passed to other calls, including invalid use of `void` results. Track call-containing expressions rather than validating every raw argument or function-body `return` statement. Underline the result expression or offending inner-call argument. Preserve gem5 assignment-only legacy exemptions for expected `NetDest`, actual `Set`, and the inverse interface relationship; do not apply those exemptions to function arguments or add blanket numeric/C++ implicit conversions. Keep this check separate from `SliccMemberDiagnostics`, whose dot-RHS check remains name-only.
   - `SliccExternalTypeNavigation` — handles declaration-name UI navigation from `structure(..., external="yes")` and `external_type` to reachable C++ type definitions or supported aliases. Applies `external_name`, `gem5::ruby` ownership (`gem5` fallback for `external_type`), template-base lookup, standard scalar/std/Scalar exemptions, and `InPort`/`OutPort` mapping to `MessageBuffer`. `SliccRubyBuildScope` locates the related Ruby Kconfig/SConscript root, collects supported literal `Source`/`MakeInclude`/`SLICC_INCLUDES` entries and transitive C++ includes, maps generated protocol headers back to their sources, and caches source/document dependencies. `SliccCppTypes` recognizes supported source-level type/alias definitions; `SliccCppTypeTarget` preserves source offsets for navigation. Missing definitions produce errors; unavailable source context produces warnings, while indexing defers lookup. Semantic SLICC references still resolve to SLICC declarations. The static graph unions conditional branches without executing Kconfig/SCons or preprocessing C++; computed paths, macro-generated definitions, and C++ member/call navigation are not implemented. See [RUBY_CPP_INTEGRATION.md](RUBY_CPP_INTEGRATION.md) for the inspected file tree and analysis boundaries.
   - `SliccMemberNavigation` — reverses structure-field and enum/state-constant declaration navigation into actual usage statements, deduplicated per statement and filtered by semantic PSI identity. Uses the shared cached `SliccFunctionNavigation.Scope` for type/machine/include ownership, direct field lookup, supported interface inheritance, and qualified enum resolution. Transition state/event references use their respective `State`/`Event` types; ambiguous unqualified constants are not guessed. `SliccUsageSite` is shared by function calls and member usages, preserving smart pointers, multiline previews, configurable truncation, and actual identifier offsets. The semantic resolver still maps references to declarations, never to usage-site UI targets.
   - `SliccGotoSymbolContributor` — Ctrl+Alt+Shift+N symbol search
   - `SliccStructureViewFactory` — Alt+7 structure tree (machine → in_ports, out_ports, actions, transitions, functions). Uses plugin-owned tree elements implementing the public structure-view interfaces with smart PSI pointers, avoiding the implementation base class unavailable in 2026.2.

6. **Completion** (`completion/`)
   - `SliccCompletionContributor` — Keyword completions + symbol completions (file-local actions/structures, visible free functions from includes/protocol/global scopes, and gem5 built-in types). Function candidates include return types and parameter signatures; completion and parameter info share `SliccGotoDeclarationHandler.collectVisibleFunctions`. Dot-triggered member completion resolves the owner's type and shows only its fields and member functions (suppresses keyword/symbol completions). Action names are suppressed inside action bodies.
   - `SliccParameterInfoHandler` — `Ctrl+P` parameter info popup; shows all overloads with parameter types, highlights the current argument. Preserve reference (`&`) and pointer (`*`) modifiers in displayed signatures and declaration/definition matching. Shared call resolution first uses `SliccExpressionTypes` nominal compatibility, ignoring both call qualifiers; only the legacy `scoreOverload` fallback ignores references while retaining pointer distinctions. Registered as `codeInsight.parameterInfo` (not `lang.parameterInfo`).
   - `SliccTypedHandler` — Auto-triggers the parameter info popup when `(` is typed, via `invokeLater` + `PsiDocumentManager.commitDocument`.

7. **Inlay Hints** (`inlay/`)
   - `SliccInlayHints` — Shared scope-aware model for parameter-name and implicit-type hints. Parameter hints reuse function-call resolution, including member ownership, includes, and overloads; ambiguous candidates must agree on the displayed parameter name. Omit unnamed parameters and arguments already named like the parameter. Type hints cover only the first actual use of each implicit `in_msg` / `out_msg` / `tbe` / `cache_entry` / `address` binding; preserve nearest matching message scopes and action boundaries, and suppress explicit visible variable declarations, including function parameters named `in_msg` or `out_msg`. Do not calculate hints while the project is indexing.
   - `SliccInlayPresentation` — Shared declarative presentation rendering. The independently configurable providers `slicc.parameter.names` and `slicc.implicit.types` appear under Settings → Editor → Inlay Hints → SLICC in the parameter-name and type groups. Provider previews live under `src/main/resources/inlayProviders/{id}/preview.sm`, with UI strings in `messages/SliccBundle.properties`.

### Cross-File Resolution Design

Two-tier scope resolution in `SliccProtocolIndex`:
- **Protocol-group scope**: files sharing the same directory are treated as one protocol unit.
- **Global scope**: `RubySlicc_interfaces.slicc` provides globally visible built-in declarations.

The shared resolver restricts `TYPE_REF` positions to type declarations. Variable resolution and type inference use the same visible-variable collection: preceding locals in the nearest enclosing blocks, function parameters, enclosing structure fields, and machine fields. Reference parameters such as `WriteMask &mask` remain single declarations; preserve modifiers in signatures while resolving member ownership through their underlying types. Explicit message-named function parameters override implicit message bindings consistently in resolution, completion, highlighting, and navigation. Comments and whitespace around member access are skipped through `psi/SliccPsiUtil`.

External C++ lookup is separate from the SLICC protocol symbol index: it remains
within the detected Ruby root and explicitly reachable dependencies, rather than
falling back to unrelated same-named definitions elsewhere in the project. Open
the full gem5 checkout to resolve common types whose headers are outside a copied
Ruby subtree.

### Registration

All extensions are registered in `src/main/resources/META-INF/plugin.xml` (fileType, parser definition, syntax highlighter, annotator, folding builder, completion contributor, parameter info handler, typed handler, reference contributor, structure view, goto symbol contributor, color settings page, declarative inlay-hint providers).

## Key Extension Points (Not Yet Implemented)

- **Rename refactoring**: `SliccNamedElement.setName()` needs implementation.
- **Code formatting**: Needs a `FormattingModelBuilder`.
