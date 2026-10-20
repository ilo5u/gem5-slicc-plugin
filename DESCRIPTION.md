Language support for gem5 Ruby SLICC: highlighting, smart completion, cross-file navigation, and call-site discovery.

## Description
Read, write, and explore gem5 Ruby cache-coherence protocols in CLion and IntelliJ IDEA. SLICC / gem5 Ruby Language Support brings syntax and semantic highlighting, context-aware completion, parameter information, and source navigation to `.sm` and `.slicc` files.
Follow transitions to their actions, navigate between function declarations and definitions, and jump back to the exact statements that call a function. Include-aware lookup and machine, member, and lexical scopes help distinguish similarly named symbols across protocol files. Inside `peek` and `enqueue`, message-variable types drive relevant member suggestions.
Built for developers and researchers working on the gem5 Ruby memory subsystem. This plugin supports the **SLICC protocol language**, not the Ruby programming language.

## Features
- **Syntax and semantic highlighting** — Distinguish SLICC keywords, types, machines, actions, functions, enumeration members, and fields. Customise colours in the IDE's SLICC colour scheme.
- **Action ↔ transition navigation** — Jump from a transition's action reference to its implementation. From an action declaration, jump to its referencing transition or choose among multiple transitions.
- **Function declaration → definition navigation** — Resolve calls and separate declarations to matching SLICC definitions, with overload-aware lookup across machine/include and member scopes.
- **Function → calling statements** — From a function definition, or a declaration without a SLICC implementation, find concrete calling statements in functions and actions. This also works for declaration-only `structure` and `external_type` methods.
- **Readable call-site previews** — Show complete multiline statements on one line, with caller, file, and line context. Jump directly for a single statement or choose among multiple statements, then navigate to the actual call. Previews have a configurable length limit, defaulting to 160 characters.
- **Include-aware function completion** — Suggest free functions from machine includes, protocol files, and global declarations, with return types and parameter signatures. Prefix completion includes functions such as `tagLatency` and `dataLatency` when their declarations are visible.
- **Type-aware member completion** — After `.`, show fields and member functions of the resolved receiver type, including supported `interface` inheritance chains. Handle chained accesses and comments around member-access operators.
- **Scoped message variables** — Suggest `in_msg` only in `peek` bodies and `out_msg` only in `enqueue` bodies. Infer their types from the second statement argument, respecting nested bindings, and flag out-of-scope usage.
- **Function parameter information** — View overload signatures and the active argument with `Ctrl+P`, or automatically when typing `(` after a function name.
- **Scope-aware references and Find Usages** — Respect preceding local declarations, block visibility, and parameter/local shadowing. Distinguish resolved overloads and same-named symbols owned by different machines or types.
- **Protocol and global symbol lookup** — Follow protocol/include relationships and declarations exposed through `RubySlicc_interfaces.slicc` to navigate across source files.
- **Everyday editing tools** — Search symbols by name, browse the Structure View, fold declaration bodies, match braces, toggle comments, and highlight unresolved ordinary identifiers.

## Current Limitations
Rename refactoring, automatic code formatting, inlay hints, and live templates are not implemented. Member completion and navigation are type-aware, but invalid member names after `.` are not currently reported as errors. The plugin provides editor support; it does not replace the gem5 SLICC compiler or run simulations.
