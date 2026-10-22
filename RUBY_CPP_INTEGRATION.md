# Ruby SLICC and C++ Integration

This document describes the source layout first inspected for the 1.3.0
external-type navigation feature and its protocol-scoped lookup rules in 1.5.0.
Paths beginning with `ruby/` refer to the Ruby source tree
included in this repository. In a complete gem5 checkout, this tree normally lives
under `src/mem/ruby/`.

## Protocol Selection and SLICC Inputs

The actual gem5 build connects configuration to SLICC inputs as follows:

```text
ruby/Kconfig
  rsource "protocol/Kconfig"
    config RUBY_PROTOCOL_MI_example
    config RUBY_PROTOCOL_MESI_Two_Level
    rsource "chi/Kconfig"
      config RUBY_PROTOCOL_CHI

ruby/SConsopts
  initializes PROTOCOL_DIRS and SLICC_INCLUDES

ruby/protocol/SConsopts
  appends its directory to PROTOCOL_DIRS
  exports protocol_base

ruby/protocol/SConscript
  reads enabled RUBY_PROTOCOL_<name> configuration entries
  searches PROTOCOL_DIRS for <name>.slicc
  invokes SLICC with RubySlicc_interfaces.slicc as a shared input
```

For example, `ruby/protocol/MI_example.slicc` includes the protocol's message,
cache, directory, and DMA `.sm` files. The separately supplied
`RubySlicc_interfaces.slicc` includes `RubySlicc_Exports.sm`,
`RubySlicc_Types.sm`, and the shared utility, component-mapping, definitions, and
memory-control declarations. A protocol does not need to explicitly include all
these shared files itself.

`ruby/Kconfig` also references `../../learning_gem5/part3/Kconfig`. In a complete
checkout, this allows protocols outside the main Ruby directory to participate in
the same configuration tree. That referenced directory is not included in this
repository's standalone Ruby snapshot.

## C++ Sources, Headers, and Generated Forwarders

The C++ side is related through several distinct source edges:

```text
ruby/network/SConscript
  Source('MessageBuffer.cc')
    ruby/network/MessageBuffer.cc
      #include "mem/ruby/network/MessageBuffer.hh"
        gem5::ruby::MessageBuffer class definition

ruby/structures/SConscript
  Source('CacheMemory.cc')
    ruby/structures/CacheMemory.cc
      #include "mem/ruby/structures/CacheMemory.hh"
        gem5::ruby::CacheMemory class definition

ruby/SConscript
  MakeInclude('network/MessageBuffer.hh')
  MakeInclude('structures/CacheMemory.hh')
    generated mem/ruby/protocol/<header basename>
      #include "<absolute path to the original source header>"
```

`MakeInclude` creates forwarding headers in the build's protocol directory; it
does not relocate the original C++ definitions. It is also important for
header-only external types such as `TBETable` and `PerfectCacheMemory`.

The SLICC builder additionally supplies
`mem/ruby/slicc_interface/RubySlicc_includes.hh` and the headers in
`SLICC_INCLUDES` to generated code. `RubySlicc_includes.hh` includes the shared
C++ component-mapping and utility headers. Following their transitive includes
can reach common gem5 types outside the Ruby directory in a complete checkout.

## Mapping an External Declaration

The navigation feature applies to `structure` declarations whose `external`
attribute is `"yes"`, and to `external_type` declarations. Ordinary SLICC
structures remain SLICC declarations and are not required to have handwritten C++
definitions.

For example, these shared and protocol-specific declarations map to actual C++ class
definitions, rather than to constructors or to forward declarations:

| SLICC declaration | C++ target |
| --- | --- |
| `structure(MessageBuffer, external="yes", ...)` | `gem5::ruby::MessageBuffer` in `ruby/network/MessageBuffer.hh` |
| `structure(CacheMemory, external="yes")` | `gem5::ruby::CacheMemory` in `ruby/structures/CacheMemory.hh` |
| `structure(MN_TBEStorage, external="yes")` | `gem5::ruby::CHI::MN_TBEStorage` in `ruby/structures/MN_TBEStorage.hh` |
| `external_type(NodeID, ...)` | `gem5::ruby::NodeID` typedef in `ruby/common/TypeDefines.hh` |

The mapping rules are:

- An `external_name` attribute overrides the SLICC name used for C++ lookup.
- An unqualified external type declared in a machine or its machine-owned literal include
  closure first uses `gem5::ruby::<protocol>::Name`, then `gem5::ruby::Name`.
  CHI's `MN_TBEStorage` therefore resolves to `gem5::ruby::CHI::MN_TBEStorage`,
  even if a shared or another protocol's same-named type exists. Only reachable
  sources participate; other protocol namespaces are never searched.
- Outside machine ownership, try `gem5::ruby::Name` before the uniquely known
  protocol candidate. An `external_type` retains the final exact `gem5::Name`
  fallback for common types such as `Addr`, `Tick`, and `Cycles`.
- Only includes written inside a machine propagate its ownership. A global
  declaration or an include outside that machine remains shared-Ruby-first even
  when the same source file also contains a machine.
- Protocol namespaces come from case-preserving `RUBY_PROTOCOL_<name>` Kconfig
  suffixes. A literal `bool` prompt is an alias with whitespace normalised to
  underscores, not a replacement namespace. Match the `.slicc` `protocol` literal
  to those names and propagate ownership through its transitive literal includes.
  Unknown or conflicting metadata leaves only the shared Ruby candidate (and the
  `external_type` gem5 fallback), rather than guessing a protocol. This lightweight
  protocol graph is cached separately from C++ source scanning and reuses per-file
  source tokens. If machine ownership is unknown or conflicting and shared lookup
  also misses, report an unavailable-context warning rather than a definite
  missing-definition error.
- A name beginning with `gem5::` uses that qualified name directly. A leading
  `::` requests an exact global qualified name. Other explicitly qualified names
  remain exact relative candidates; none are broadened to other namespaces.
- Multiple definitions of the same exact qualified candidate are offered for
  selection. Unqualified lookup never searches arbitrary descendant namespaces,
  nested-class-only names, anonymous namespaces, or `gem5::rubyLike`.
- Template arguments are removed when locating the underlying type definition.
- Supported `typedef` and `using Name = ...` aliases are valid navigation
  targets. Navigation stops at the alias declaration; it does not promise to
  resolve an arbitrary alias chain.
- Standard C++ built-in scalar types, fixed-width integer spellings, `std::`
  types, and the SLICC `Scalar` placeholder are exempt from missing-definition
  errors.
- SLICC's logical `InPort` and `OutPort` types map to `MessageBuffer`; they are
  not required to have separate C++ classes.

The `primitive="yes"` attribute alone does not disable checking: for example,
`MessageBuffer` is marked primitive but still has a real C++ definition.

## Navigation and Diagnostics

Place the caret on the name in an external SLICC declaration and use **Ctrl+B**
or **Ctrl+Click** to open the matching C++ type definition. If the supported source
graph contains multiple matching definitions, the IDE can offer a target chooser.
Generated forwarding headers are mapped back to their original source headers,
so a prior gem5 build is not required for this mapping.

Type usages inside SLICC still resolve semantically to their SLICC declarations.
This preserves SLICC member completion, references, and existing SLICC navigation;
the C++ jump is a declaration-name UI action. This feature does not implement
navigation from SLICC member functions or fields to individual C++ members, or C++
call resolution.

When a Ruby source context is available but no matching definition is found, the
external declaration receives an error identifying the expected qualified C++
name. Check its `external_name`, `Source` and `MakeInclude` entries, and reachable
headers. If the Ruby Kconfig/SConscript context cannot be located or read, a
warning explains that the source context is unavailable instead of asserting that
the C++ type does not exist. Lookup is deferred while the IDE is indexing.

## Static Analysis Boundaries

The implementation reads source text; it does not execute Kconfig, Python,
SCons, the SLICC compiler, or the C++ compiler. It recognizes the Ruby root from
its `Kconfig`, `SConscript`, and `protocol` directory, reads related literal
Kconfig include paths and build scripts, and uses literal `Source`, `MakeInclude`,
`SLICC_INCLUDES`, and C++ include paths to construct a conservative dependency
graph.

This graph is a union of statically visible conditional branches, not a model of
the currently selected build configuration. For example, a CHI-only literal
source or `MakeInclude` entry can be considered even when CHI is not selected.
The actual build's `RUBY_PROTOCOL_` and `PROTOCOL_DIRS` processing explains the
source organization; the plugin does not evaluate those Python expressions or
reproduce their active configuration decisions.

Macro-generated C++ declarations, computed include or source paths, arbitrary
Python build logic, preprocessor selection, and complex alias syntax are outside
this source-based analysis. A definition present only through unsupported edges
may therefore be reported as missing. For complete common gem5 types, open the
full checkout rather than only a copied Ruby subtree.

Resolution stays attached to the detected Ruby source root and its reachable
dependencies, including explicitly related external protocol directories. It does
not fall back to a project-wide same-name search across unrelated Ruby checkouts.
Source and document changes invalidate the cached lookup data. Each annotation
session shares one source context across its external declarations. Diagnostics
inspect source locations without loading C++ PSI or creating navigation pointers;
those targets are created only for an explicit navigation request.
Protocol token caches live in project-owned storage keyed by source file, not in
VFS user data; this preserves their project lifetime and avoids the IDE's VFS
cache leak guard, which would otherwise interrupt external-type navigation.

## Implementation Entry Points

- `navigation/SliccExternalTypeNavigation.java`: external attributes, type names,
  namespace rules, exemptions, and lookup results.
- `navigation/SliccRubyBuildScope.java`: Ruby root detection, source dependency
  graph, generated-header forwarding, and caching.
- `navigation/SliccCppTypes.java`: supported source-level C++ type and alias
  definition recognition.
- `navigation/SliccCppTypeTarget.java`: C++ definition labels and source offsets.
- `navigation/SliccGotoDeclarationHandler.java`: declaration-name C++ navigation.
- `highlighting/SliccAnnotator.java`: missing-definition errors and
  unavailable-context warnings.

These implementation paths are relative to `src/main/java/com/slicc/plugin/`.
