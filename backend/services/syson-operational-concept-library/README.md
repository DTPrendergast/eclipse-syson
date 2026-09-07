# syson-operational-concept-library

Adds `OperationalConcept`, an attribute-free marker `MetadataDefinition`, to the set of
standard libraries SysON loads into every project, plus an explorer icon distinguishing
elements it's applied to. See `docs/plan.md`, item 2.

## Why this is so small

An operational concept is, per the plan, "a package containing use cases, stakeholder/actor
parts, and a documentation body" — and all of that (`UseCaseUsage`, `PartUsage`,
`Documentation`) is already ordinary SysML v2, already creatable via the tree's generic
"New Object" tool (see item 1's finding: tree/root creation is metamodel-driven, not
curated). Narrative authoring is already covered too: `SysMLv2PropertiesConfigurer`
already puts a `Documentation`-editing text area in the standard Details panel for any
element (`createDocumentationWidget()`, gated only on "not an `AnnotatingElement`") — so
there was no need to build a bespoke form or markdown representation to satisfy the
acceptance criterion ("a user can author an operational concept narrative"). The one
genuinely missing piece is making such a package **identifiable** and **distinguished in
the explorer**, which is what this module adds.

**Deferred, not built:** a dedicated "New Operational Concept" one-step creation tool
(create + tag + seed with starter content) and an explorer show/hide filter for operational
concepts. Neither has a low-risk extension point the way the icon does (see below) — tree
creation tools have no per-type Java hook the way tables do, and the filter mechanism
(`ISysONExplorerFilterService`) dispatches on a fixed, upstream-owned set of filter ids
rather than a pluggable list. Today, a user creates a package via the generic tool and
types `@OperationalConcept;` into it (or pastes a larger snippet) via the existing textual
insert capability. Revisit if this friction turns out to matter in practice.

## How the library loads without any upstream code changes

Same mechanism as `syson-verification-library` — see that module's README.md. This module
ships its own `src/main/resources/sysml.libraries/OperationalConcept.json`, picked up
automatically by `SysONDefaultLibrariesConfiguration`'s `classpath*:sysml.libraries/*.json`
scan.

## How the explorer icon works without any upstream code changes

`SysMLv2LabelService` (the default label/icon computation for SysML elements) implements
`ILabelServiceDelegate`, a genuine Sirius Web chain-of-responsibility extension point:
multiple delegates can be registered as Spring beans, each opting in via `canHandle(Object)`
to the objects it wants to render, declining (returning `false`) everything else so the
next delegate in the chain gets a turn. `OperationalConceptLabelServiceDelegate` is a
second, additive `@Service implements ILabelServiceDelegate` that only opts in for elements
carrying the `OperationalConcept` metadata, giving them a dedicated icon; everything else
falls through to `SysMLv2LabelService` unchanged.

## Layout

- `src/main/resources/operational-concept-library-source/OperationalConcept.sysml` — the
  canonical, human-authored source of truth. Edit this file, not the JSON.
- `src/main/resources/sysml.libraries/OperationalConcept.json` — the compiled artifact
  actually loaded at runtime. Generated from the `.sysml` source above; do not hand-edit.
- `src/main/java/.../OperationalConceptLabelServiceDelegate.java` — the explorer icon
  delegate.
- `src/test/java/.../OperationalConceptLibraryGenerator.java` — the (manually-run, not
  part of CI) generator that produces the JSON from the `.sysml` source.
- `src/test/java/.../OperationalConceptLibraryLoadingTest.java` and
  `OperationalConceptLabelServiceDelegateTests.java` — automated tests.

## Regenerating after editing the `.sysml` source

```
mvn test -pl backend/services/syson-operational-concept-library \
    -Dtest=OperationalConceptLibraryGenerator#generate -DfailIfNoTests=false
```

Then review and commit both files together (see `syson-verification-library`'s README.md
for what's worth reviewing in the regenerated JSON).
