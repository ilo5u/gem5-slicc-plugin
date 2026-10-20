# Changelog

## 1.2.0 — Unreleased

### Added

- Navigate from function calls and separate declarations to matching definitions, including cross-file includes and overloads. On a definition name, or a declaration with no SLICC definition (including structure/external-type methods), jump to the sole calling statement or choose among multiple statements in functions/actions. Show the complete multiline statement on one line with file/line context and navigate to the actual call offset. Keep separate statements in the same caller; merge repeated calls within one statement. Labels default to 160 Unicode characters including an ellipsis, configurable with the IDE VM option `-Dslicc.navigation.maxStatementLength=240`. Calls to declaration-only functions still navigate to their declarations; keep machine/include/type scopes separate, including shared prototypes with machine-specific definitions.
- Navigate from an action declaration name to the transitions that use it with Ctrl+Click or Ctrl+B. Jump directly for one matching transition or show a chooser for multiple matches, with transition headers and file names; resolve action names within their machine/include scope.
- Complete `in_msg` only inside `peek` bodies and `out_msg` only inside `enqueue` bodies. Infer each variable's type from the corresponding statement's second argument for member completion and navigation.
- Report message-variable usage outside its scope as an error. Support nested blocks, nearest matching message bindings, and restoration of outer bindings after an inner block ends.
- Add Marketplace HTML listing/release notes and a separate HTML getting-started guide. Synchronize the product description and 1.2.0 What's New into the plugin descriptor, preserving historical release notes.

### Fixed

- Resolve function calls across declaration/definition files before choosing an overload. Correct zero-argument calls and retain primitive, scoped, and pointer parameter types when matching signatures; preserve local shadowing and separate member functions from free functions.
- Include visible free functions from machine includes, protocol files, and global declarations in prefix completion. Show return types and parameter signatures, including overloads, for functions such as `tagLatency` and `dataLatency`; use the same visible function set for parameter information.
- Replace the structure view's unavailable implementation base class with plugin-owned tree elements using public interfaces and smart PSI pointers, restoring compilation against the 2026.2 platform.
- Resolve ordinary variables using lexical scope so locals cannot leak between actions or outside their declaring blocks, or be used before declaration.
- Respect local-variable and parameter shadowing when inferring types for member completion, navigation, and function overload resolution.
- Match Find Usages results against resolved declarations so fields and function overloads with the same name are not conflated.
- Support comments and whitespace on either side of `.` in member completion and navigation, including chained accesses.

### Validation

- Compilation passed against local CLion 2026.1 and 2026.2.3.1 SDKs.
- The plugin compiled against 2026.1 passed 120 temporary real-PSI regression checks on each SDK, covering scopes, types, Find Usages, overloads, completions, message variables, and structure views.
- An additional 21 temporary checks passed for transitive/repeated/cyclic includes, `t`/`d` prefix candidates, function signatures, overloads, and parameter information using a controlled protocol scope.
- An additional 90 temporary checks passed for action/transition navigation: zero/one/multiple candidates, duplicate calls, resource lists, same-named actions in different machines, partial bodies, shared includes across machines, candidate presentations, and preservation of semantic references, using controlled include scopes.
- An additional 202 temporary real-PSI checks passed on each SDK for function declaration/definition/caller navigation, overloads, primitive/literal/pointer arguments, member ownership and inheritance, qualified calls, recursion, shadowing, shared declarations with machine-specific definitions, cross-file includes, Find Usages identity, and preservation of action and constructor navigation, using controlled include scopes.
- An additional 112 temporary real-PSI checks passed on each SDK for declaration-only caller navigation, including structure/external-type methods, overloads, inherited members, duplicate calls, zero/one/multiple callers, machine/type isolation, controlled cross-file scopes, and preservation of call-to-declaration and prototype-to-definition navigation.
- An additional 141 temporary real-PSI checks passed on each SDK for statement-based call-site candidates, multiline statements/comments, variable initializers, conditions, calls inside built-in statements, same-caller distinct statements, same-statement deduplication, chooser labels, configurable Unicode-safe truncation, smart pointers, actual call offsets/line numbers, and preservation of overload/type ownership and forward navigation.
- Plugin structure verification passed. The temporary checks do not constitute a persistent automated test suite.

These notes are a release draft. The user-facing copy in `MARKETPLACE.html` is
synchronized into `src/main/resources/META-INF/plugin.xml` under `<description>`
and `<change-notes>`, preserving the historical 1.1.0 and 1.0.1 entries. Keep these
copies aligned while finalizing the 1.2.0 release; `GET_STARTED.html` contains the
separate onboarding guide.
