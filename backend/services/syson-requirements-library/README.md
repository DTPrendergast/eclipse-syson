# syson-requirements-library

Adds `ThresholdOrObjective` (attribute `kind : ThresholdOrObjectiveKind`) and
`RequirementCategory` (attribute `category : String`), two small `MetadataDefinition`s, to
the set of standard libraries SysON loads into every project. Both are applied on a
`RequirementUsage`. See `docs/plan.md`, item 3 (task 3b), for why these two specifically
needed adding — neither concept exists anywhere in the bundled standard library.

"Requirement Type" (Functional, Interface, Performance, Physical, Design Constraint), by
contrast, does not need anything from this library: it is modeled by specializing the
standard library's own `Requirements::FunctionalRequirementCheck` /
`InterfaceRequirementCheck` / `PerformanceRequirementCheck` / `PhysicalRequirementCheck` /
`DesignConstraintCheck` `RequirementDefinition`s — see `RTVRequirementTypeMapping`'s Javadoc
in `syson-table-requirements-view`.

## How this loads without any upstream code changes

`SysONDefaultLibrariesConfiguration` (in `syson-application-configuration`) loads the
"SysML Standard Library" family by scanning `classpath*:sysml.libraries/*.json` — Spring's
`classpath*:` prefix aggregates matches across **every** jar on the classpath, not just the
one that defines the scan. So this module ships its own
`src/main/resources/sysml.libraries/RequirementAttributes.json`, and it gets picked up
automatically as part of the existing SysML standard library, with no changes needed to
`SysONDefaultLibrariesConfiguration` or `SysONApplication`'s component scan.

## Layout

- `src/main/resources/requirements-library-source/RequirementAttributes.sysml` — the
  canonical, human-authored source of truth. Edit this file, not the JSON.
- `src/main/resources/sysml.libraries/RequirementAttributes.json` — the compiled artifact
  actually loaded at runtime, in the same Sirius EMF-JSON format as the other files in that
  directory. Generated from the `.sysml` source above; do not hand-edit.
- `src/test/java/.../RequirementAttributesLibraryGenerator.java` — the (manually-run, not
  part of CI) generator that produces the JSON from the `.sysml` source. See its class
  Javadoc for the command to run it.
- `src/test/java/.../RequirementAttributesLibraryLoadingTest.java` — an automated test that
  the JSON, as checked in, actually loads and defines what's expected.
- `src/main/java/.../RequirementAttributesQueryServices.java` — reads the applied metadata
  back off a `RequirementUsage`. Consumed by `syson-table-requirements-view`'s
  `RTVQueryServices`/`RTVMutationServices`, mirroring how `syson-verification-library`'s
  `VerificationLinkQueryServices` is consumed by the same module.

## Regenerating after editing the `.sysml` source

```
mvn test -pl backend/services/syson-requirements-library \
    -Dtest=RequirementAttributesLibraryGenerator#generate -DfailIfNoTests=false
```

Then review the diff to `RequirementAttributes.json` (it will look like a full rewrite —
internal EMF-JSON ids are freshly generated on every run — but the semantic content, i.e.
the declared names, attribute types, and `annotatedElement` restriction, should be the only
thing worth actually reviewing) and commit both files together.
