# syson-verification-library

Adds `VerificationProcedure`, a small `MetadataDefinition` carrying `procedureId`, to the
set of standard libraries SysON loads into every project. It is meant to be applied on a
`VerificationCaseUsage` alongside the standard library's own `VerificationMethod` metadata
(`VerificationMethodKind`: test/demo/analyze/inspect) rather than duplicating it — see
`docs/plan.md`, item 1, for why only `procedureId` needed adding.

## How this loads without any upstream code changes

`SysONDefaultLibrariesConfiguration` (in `syson-application-configuration`) loads the
"SysML Standard Library" family by scanning `classpath*:sysml.libraries/*.json` — Spring's
`classpath*:` prefix aggregates matches across **every** jar on the classpath, not just the
one that defines the scan. So this module ships its own
`src/main/resources/sysml.libraries/VerificationProcedure.json`, and it gets picked up
automatically as part of the existing SysML standard library, with no changes needed to
`SysONDefaultLibrariesConfiguration` or `SysONApplication`'s component scan.

## Layout

- `src/main/resources/verification-library-source/VerificationProcedure.sysml` — the
  canonical, human-authored source of truth. Edit this file, not the JSON.
- `src/main/resources/sysml.libraries/VerificationProcedure.json` — the compiled artifact
  actually loaded at runtime, in the same Sirius EMF-JSON format as the other files in that
  directory. Generated from the `.sysml` source above; do not hand-edit.
- `src/test/java/.../VerificationProcedureLibraryGenerator.java` — the (manually-run, not
  part of CI) generator that produces the JSON from the `.sysml` source. See its class
  Javadoc for the command to run it.
- `src/test/java/.../VerificationProcedureLibraryLoadingTest.java` — an automated test that
  the JSON, as checked in, actually loads and defines what's expected.

## Regenerating after editing the `.sysml` source

```
mvn test -pl backend/services/syson-verification-library \
    -Dtest=VerificationProcedureLibraryGenerator#generate -DfailIfNoTests=false
```

Then review the diff to `VerificationProcedure.json` (it will look like a full rewrite —
internal EMF-JSON ids are freshly generated on every run — but the semantic content, i.e.
the declared names, attribute types, and `annotatedElement` restriction, should be the only
thing worth actually reviewing) and commit both files together.
