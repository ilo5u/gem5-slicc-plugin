# SLICC / gem5 Ruby Language Plugin for CLion / IntelliJ IDEA

[English](README.md) | [简体中文](README.zh-CN.md)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

Language support for `.sm` and `.slicc` files used in the
[gem5](https://www.gem5.org) Ruby memory-subsystem cache coherence framework.

Version **1.5.0** — **2026-10-22**. See [CHANGELOG.md](CHANGELOG.md) for the release notes.

## How to Use

1. **Install the plugin in CLion.** Follow the [installation instructions](#installing-in-clion--intellij-idea), then wait for project indexing to finish after opening a project.
2. **Try the bundled gem5 Ruby sources.** This repository's [`ruby/`](ruby/) folder contains the main gem5 Ruby code, including SLICC protocols and related C++ implementations. Use **File → Open** in CLion to open that folder as a project. Open a protocol file such as [`MI_example-cache.sm`](ruby/protocol/MI_example-cache.sm) to try highlighting, completion, inlay hints, and `Ctrl+Click` / `Ctrl+B` navigation.
3. **For gem5 development, open the full gem5 repository root.** Prefer opening the gem5 root as the CLion project rather than only `src/mem/ruby` or an individual protocol directory. This keeps C++ sources, `.sm` / `.slicc` files, and headers outside the Ruby subtree available together for indexing and cross-file navigation. The bundled `ruby/` folder is useful for a quick preview; resolving dependencies outside that copied subtree requires a full gem5 checkout.
4. **Explore descriptions and diagnostics.** Hover a declaration or resolved usage, or press `Ctrl+Q`, to read its `desc`. In actions, try `DPRINTF(` for project-wide debug-flag completion; literal format argument-count mismatches produce warnings, while definite flag, transition, and function-name problems remain errors.

### Prefer VS Code?

If you prefer developing gem5 SLICC protocols in **Visual Studio Code**, use the
companion [gem5-slicc-plugin-for-vscode](https://github.com/ilo5u/gem5-slicc-plugin-for-vscode)
extension. This repository contains the CLion / IntelliJ IDEA plugin; see the
VS Code repository for its own installation and usage instructions.

## Documentation

Marketplace description, features, and 1.5.0 What's New are available in [MARKETPLACE.html](MARKETPLACE.html).
The standalone Markdown description is available in [DESCRIPTION.md](DESCRIPTION.md).
The HTML onboarding guide is available in [GET_STARTED.html](GET_STARTED.html).

The product description and 1.5.0 release notes are also synchronized into
`src/main/resources/META-INF/plugin.xml` (`<description>` and `<change-notes>`).
Keep these copies aligned when editing the Marketplace HTML. Marketplace normally
uses the uploaded plugin's metadata; if you have selected a permanent page-level
description override, change that setting to use `plugin.xml` again. See the
[official listing guidance](https://plugins.jetbrains.com/docs/marketplace/best-practices-for-listing.html#plugin-description).

---

## Implemented Features

| Feature | Detail |
|---------|--------|
| **Syntax highlighting** | Declaration keywords (`machine`, `structure`, `action`, `transition`, …), control flow, type keywords, built-in SLICC statements (`peek`, `enqueue`, `trigger`, `stall_and_wait`, `new`, …), attribute keywords, comments, strings, numbers |
| **Semantic highlighting** | Machine / structure / enumeration names highlighted as class names; action / function names as function declarations; enum members as constants; structure fields as instance fields; implicit variables (`tbe`, `cache_entry`, `address`) as predefined symbols |
| **File and symbol icons** | Rounded-square `SM` and `H` icons distinguish `.sm` and `.slicc` files; lettered circular badges identify actions, enumerations, functions, machines, structures, and transitions. Light/dark variants share font-independent outlined lettering |
| **Additional attributes and built-ins** | Highlight/complete `block_on`, `format`, `wake_up`, `rsc_stall_handler`, `prot_stall_handler`, `return_by_ref`, and `return_by_pointer` like `main` / `desc`, and parse/highlight/complete `DPRINTF`, `DPRINTFN`, `APPEND_TRANSITION_COMMENT`, and `stallPort` without changing the 30-word reserved list |
| **Debug flags and format counts** | Complete `DPRINTF`'s first argument from literal `DebugFlag` / `CompoundFlag` declarations in all gem5/project `SConscript` files; missing/definitely unknown flags and missing format strings are errors. Warn on literal `DPRINTF` / `DPRINTFN` argument-count mismatches, including `%%`, star width/precision, and nested calls, without checking argument types |
| **Transition and call diagnostics** | Report missing actions, later duplicate state/event transition pairs in include order, and missing visible free/scoped function names. Preserve machine scope and do not confuse overload argument mismatches with missing functions |
| **Description documentation** | Hover or `Ctrl+Q` on declarations or resolved usages to display `desc`, including structures, fields, enums/constants, functions, and actions; matching function declarations/definitions can share a description |
| **Go-to-Declaration** | `Ctrl+Click` (or `Ctrl+B`) on any identifier jumps to its definition, with block-scoped variables, parameter/local shadowing, and overload-aware resolution for functions |
| **Implicit action variable navigation** | `Ctrl+Click` / `Ctrl+B` on implicit `cache_entry` opens the machine's primary `AbstractCacheEntry` implementation (such as `Entry` or CHI's `CacheEntry`); `main="false"` excludes non-default entries, while omitted `main` defaults to true. Implicit `tbe` opens `TBE`. Explicit parameters and local variables take priority; missing or ambiguous types do not select unrelated declarations |
| **External SLICC types → C++ definitions** | `Ctrl+Click` / `Ctrl+B` on a `structure` name marked `external="yes"`, or an `external_type` declaration name, opens matching reachable gem5 C++ type definitions or supported aliases. Machine-owned unqualified names prefer `gem5::ruby::<protocol>::Name`, then `gem5::ruby::Name`, without searching other protocols. Keep qualified `external_name` values exact, follow literal `Source` / `MakeInclude` entries and transitive includes, and keep unrelated Ruby roots separate. Missing definitions are errors; unavailable source context produces warnings |
| **Action → transitions** | `Ctrl+Click` / `Ctrl+B` on an action declaration name jumps to its sole referencing transition, or opens a chooser showing transition headers and files when there are multiple matches |
| **Structure fields / enum constants → usages** | `Ctrl+Click` / `Ctrl+B` on a structure field or enumeration constant declaration finds its actual usage statements, including reads, writes, initializers, and state/event references in transitions. Jump directly for one statement or choose among multiple statements with file/line context; preserve receiver type, inheritance, shadowing, and machine/include ownership |
| **Enumeration default navigation** | Resolve non-string `EnumName_MemberName` defaults to the visible enumeration member, respecting the declared enum type, ownership, and unambiguous underscore matching |
| **Generated enum/state helpers** | Recognise global `EnumName_to_string`, machine-local `MachineName_EnumName_to_string`, and state helpers such as `L1Cache_State_to_permission`. Follow gem5's generated type identifier, including external/primitive type names or `external_name`; use function-call highlighting and declaration navigation while preserving explicit functions/variables and scope |
| **Function navigation** | Calls and separate declarations jump to the matching definition; clicking a definition name, or a declaration with no SLICC definition (including structure/external-type methods), lists concrete calling statements in functions/actions and navigates to the call's line. One statement jumps directly; multiple statements open a chooser with file/line locations. Multiline statements display on one line, capped at 160 characters with an ellipsis; override using the IDE VM option `-Dslicc.navigation.maxStatementLength=240`. Match parameter signatures and machine/include/type scopes; calls to declaration-only functions still navigate to the declaration |
| **Go to Symbol** | `Ctrl+Alt+Shift+N` to search for any action, function, machine, or type by name |
| **Find Usages** | `Alt+F7` on any named declaration finds its reference sites, distinguishing same-named fields, local variables, and resolved function overloads |
| **Auto-completion** | All SLICC keywords, gem5 built-in types, file-local symbols, and free functions visible through includes, protocol files, and global declarations. Function candidates show return types and parameter signatures; dot-triggered member completion (`tbe.`, `in_msg.`, `someVar.`) shows fields and functions of the resolved type |
| **Declaration-driven port methods** | `in_port` names have type `InPort` and `out_port` names have type `OutPort`, not the second message-type argument. Visible SLICC declarations drive member completion, navigation, parameter information, return-type inference, and member checks. `isReady` and `dequeue` are ordinary method/function identifiers, not keywords or built-ins; preserve scopes and explicit variable shadowing |
| **Parameter info** | `Ctrl+P` (or auto-popup on `(`) shows all overloads of a function with parameter types; current argument position is highlighted |
| **Parameter name inlay hints** | Show parameter names before arguments of resolved free-function and member-function calls. Suppress unnamed parameters, arguments with the same name, and conflicting names from ambiguous overloads |
| **Implicit type inlay hints** | Show the inferred type at the first actual use of each scoped `in_msg`, `out_msg`, `tbe`, `cache_entry`, or `address` binding, without repeating explicit variable declarations |
| **Scoped message variables** | Implicit `in_msg` is available only inside `peek` bodies and `out_msg` only inside `enqueue` bodies, including nested blocks; their types come from the statement's second argument. An explicit function parameter named `in_msg` or `out_msg` takes priority throughout the function, even inside nested message blocks, and uses its declared type for resolution, completion, navigation, and highlighting |
| **Reference parameters** | Recognise parameters such as `CHIRequestMsg & out_msg` and `WriteMask &mask` as single declarations. Preserve `&` and `*` in signatures and declaration/definition matching; use underlying SLICC types for member lookup and supported call-result compatibility |
| **Member access with comments** | Member completion and navigation support whitespace and comments on either side of `.`, including chained accesses |
| **Type-aware member error checking** | Flag unknown field and method names after `.` using the uniquely resolved receiver's visible SLICC declaration and complete `interface` hierarchy, including external declarations. Respect lexical shadowing, message scopes, reference/pointer parameters, and machine/include ownership; avoid cascading errors along an unresolved chain |
| **Function-result type checking** | Check function results assigned to declared variables or fields, including initializers, and function results passed as arguments. Infer supported simple expressions, field accesses, nested calls, and parentheses; report only definite mismatches in visible SLICC types |
| **Unresolved identifier errors** | Identifiers that cannot be resolved to any declaration are underlined in red |
| **Structure View** | `Alt+7` shows a tree: machine → in_ports, out_ports, actions, transitions, functions |
| **Code Folding** | `machine`, `in_port`, `action`, and free-standing function bodies can be collapsed |
| **Brace Matching** | Auto-pair and highlight matching `{ }`, `( )`, `[ ]` |
| **Comment Toggle** | `Ctrl+/` for line comment, `Ctrl+Shift+/` for block comment |
| **Colour Customisation** | Settings → Editor → Color Scheme → **SLICC (gem5 Ruby)** |
| **Cross-file resolution** | Identifiers resolve across all files in the same protocol directory, plus global declarations in `RubySlicc_interfaces.slicc` |

### External Type Navigation

Open the Ruby source tree with its `Kconfig`, `SConscript`, protocol declarations,
and C++ sources intact; prefer the complete gem5 checkout when headers outside
Ruby are needed. On an external declaration such as `MessageBuffer` or
`CacheMemory` in `RubySlicc_Types.sm`, use `Ctrl+B` to open the C++ definition.
Ordinary SLICC type usages still resolve to their SLICC declarations.

For a machine or its included declarations, unqualified external names first use
the owning protocol namespace, then the shared `gem5::ruby` namespace. For example,
CHI's `MN_TBEStorage` resolves to `gem5::ruby::CHI::MN_TBEStorage`, not another
protocol's same-named type. Protocol ownership follows the `.slicc` declaration and
literal includes, using `RUBY_PROTOCOL_<name>` Kconfig entries and their optional
`bool` prompt aliases; prompt whitespace is normalised to underscores. Unknown or
ambiguous protocol ownership does not trigger a search through other namespaces.
Only includes issued inside a machine inherit machine ownership; globals and
includes outside the machine retain shared-Ruby-first lookup, even in the same
file. If machine protocol ownership is unknown or ambiguous and no shared match
exists, report unavailable context as a warning rather than claiming the C++
definition is definitely missing.

Lookup is a conservative source-based graph of supported literal
`Source` / `MakeInclude` / include paths, including conditional branches together.
It does not execute Kconfig/SCons or preprocess C++; computed paths and
macro-generated definitions may not resolve. C++ member/call navigation is not
included. See [RUBY_CPP_INTEGRATION.md](RUBY_CPP_INTEGRATION.md) for the inspected
file tree, namespace/alias rules, exemptions, and diagnostic boundaries.

### Inlay Hints

Parameter names and implicit types are independently switchable under
**Settings → Editor → Inlay Hints → SLICC** in version 1.5.0.

Parameter names follow resolved function signatures and machine/include/member
scopes. When overload resolution is ambiguous, a hint is shown only where the
candidate parameter names agree. Unnamed parameters and arguments already named
like their parameter do not receive a hint.

Type hints cover implicit SLICC variables only: absent a matching explicit function
parameter, `in_msg` and `out_msg` take the second argument of their nearest
enclosing `peek` or `enqueue`; `tbe`, `cache_entry`, and `address` are limited to
actions. Each binding is annotated at its first actual use; explicit visible
variables, including `in_msg` / `out_msg` parameters, are not annotated again. Hint
calculation waits until indexing has finished.

### Function Parameters

The plugin recognises reference parameters such as `WriteMask &mask`
and `CHIRequestMsg & out_msg`, alongside pointer parameters such as `Packet *pkt`.
References and pointers remain part of the displayed signature and
declaration/definition matching. Member lookup and supported call-result
compatibility use the underlying SLICC type rather than C++ qualifier conversions.

A function parameter named `in_msg` or `out_msg` takes priority over implicit
message bindings throughout that function, including nested `peek` / `enqueue`
bodies. Its usages resolve and navigate to the parameter, receive ordinary
parameter highlighting and completion, and do not receive redundant implicit-type
inlay hints. Without such a parameter, the existing message-body restrictions and
nearest matching statement's second-argument type still apply.

### Member Error Checking

The plugin reports an error on an unknown field or method name after
`.` when the receiver type and its complete `interface` hierarchy can be resolved
unambiguously. It checks members exposed by visible SLICC declarations, including
`external="yes"` structures and `external_type`, rather than searching C++ classes
for additional undeclared members. Existing declared members are accepted by name;
this is not complete argument-compatibility or C++ type checking.

Simple receivers and field-access chains use lexical shadowing, machine/include
ownership, scoped message types, and the underlying types of reference/pointer
parameters. Unknown receivers, missing or cyclic interfaces, ambiguous same-scope
types, malformed declarations, and indexing defer diagnostics instead of guessing.
Receivers such as call results, parenthesized expressions, and indexed expressions
are not inferred for this check. An unresolved chain element does not cause
additional errors on its downstream members.

### Function-Result Type Checking

The plugin checks a function result against the type of its
assignment target, including a variable initializer, or the parameter receiving
that result in another call. For example, if `getCount()` returns `int`,
`bool ready := getCount()` reports a mismatch on the result expression. Calls
such as `consumeCount(getMessage())` are checked against visible parameter types.
Using a `void` result as a value is also an error.

Inference covers simple variables, fields, free/member function calls, nested
calls, and parentheses, respecting lexical, message, machine/include, and type
ownership. Candidate selection uses argument types, not the desired assignment
type. Compatibility follows supported SLICC nominal types and `interface`
relationships; reference/pointer call qualifiers are not C++ conversion checks.
Unknown expressions, ambiguous type declarations, conflicting overloads,
incomplete declarations, complex operators, indexed expressions, and indexing
defer diagnostics. Overloads agreeing on the inferred return type can still
provide a definite result type. This is not validation of
every raw argument, a function body's `return` statements, or full C++ implicit
conversions. Existing member-name checking remains separate.

## Supported Reserved Words

Version 1.5.0 includes highlighting and completion for all 30 words in the gem5
SLICC reserved-word list. Keywords are case-sensitive, including uppercase `OOD`.

```text
protocol include global machine in_port out_port action transition
structure external_type enumeration state_declaration
peek stall_and_wait wakeup_port enqueue check_allocate check_next_cycle
check_stop_slots check_on_cache_probe static_cast
if is_valid is_invalid else return void new OOD defer_enqueueing
```

`in_trans`, `out_trans`, `main`, `block_on`, `format`, `wake_up`,
`rsc_stall_handler`, `prot_stall_handler`, `return_by_ref`, and `return_by_pointer` are also recognised as
attribute keywords alongside `interface` and `desc`. `DPRINTF`, `DPRINTFN`,
`APPEND_TRANSITION_COMMENT`, and `stallPort` are recognised built-in calls, not additions to the
30-word reserved list. Existing additional SLICC keywords and built-ins remain supported.
`isReady` and `dequeue` are ordinary identifiers resolved through visible method
or function declarations, not special keywords or built-in calls.

### Debug Flags and Format Strings

In `DPRINTF(RubySlicc, "address=%#x count=%d", address, count)`, the first argument
is a debug flag, followed by the format string and values. Completion reads
literal `DebugFlag` / `CompoundFlag` declarations from all `SConscript` files in
the gem5 checkout or current project, including non-Ruby flags such as `HtmMem`
from `src/mem/SConscript`.
A missing flag is an error; unavailable or incomplete source context defers
unknown-flag errors. `DPRINTFN("count=%d", count)` has no flag argument.

Literal-format count mismatches in `DPRINTF` / `DPRINTFN` are warnings, not errors;
flag diagnostics and missing format strings remain errors. These checks compare
argument counts, not types. `%%` consumes no
argument; in `%*.*f`, width, precision, and value each consume one. Nested function
commas do not split outer arguments. Dynamic or unsupported formats and incomplete
calls defer the count check; these checks do not execute SCons or preprocess C++.

### Transition, Function, and Description Assistance

Transition checks resolve action names and expand state/event sets to detect later
duplicate pairs in machine/include order. State/event lists accept whitespace,
comma, and semicolon separators; the third next-state argument may be `*` without
disabling those checks. Missing-function diagnostics check
visible free and supported scoped names, not full overload argument compatibility.
Dot-member name diagnostics remain separate. Ambiguous or incomplete scopes,
unknown receivers, and indexing defer the corresponding checks rather than guessing.

Hover a declaration or a resolved usage, or press `Ctrl+Q`, to display its own
`desc`. Structures, fields, enumerations/constants, functions, and actions are
supported. Descriptions preserve line breaks and escape HTML; same-signature
function prototypes and definitions can supply each other's description. A symbol
without a description does not inherit its enclosing declaration's text.

File analysis reuses protocol/include graphs, per-file symbol indexes, and
annotation-session C++ lookup context to avoid repeated project and PSI scans.
Relevant source edits, project-root changes, and indexing-state changes invalidate
the appropriate caches. This is editor support, not a replacement for the SLICC
compiler's grammar or diagnostics.

## Not Yet Implemented

- **Rename refactoring** (`Shift+F6`) — `SliccNamedElement.setName()` is not implemented; renaming a symbol does not update all usages.
- **Code formatter** — No `FormattingModelBuilder`; auto-indent and reformat (`Ctrl+Alt+L`) are not available.
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
# Output: build/distributions/slicc-plugin-1.5.0.zip

# Launch a sandboxed IDE with the plugin loaded (primary way to test)
./gradlew runIde
```

---

## Installing in CLion / IntelliJ IDEA

1. Go to **Settings → Plugins → ⚙ → Install Plugin from Disk…**
2. Select `build/distributions/slicc-plugin-1.5.0.zip`
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
    │   ├── inlay/
    │   │   ├── SliccInlayHints.java              # Scope-aware parameter/type hint model
    │   │   └── SliccInlayPresentation.java       # Declarative hint rendering
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
