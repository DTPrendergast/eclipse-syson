# Implementation Plan

Four capabilities layered onto Eclipse SysON (base: upstream tag `v2026.7.4`).
Read `CLAUDE.md` first for architectural constraints.

**Recommended order:** 3 → 4 → 2 → 1. Items 3 and 4 are mostly model-library and view
wiring, which is low risk and teaches the view-description patterns needed for item 2.
Item 1 depends on knowing the mutation services well, so it comes last.

---

## 1. Verification objects

**Goal:** Objects with an ID, a verification type (test, demo, analysis, inspection), and a
procedure, linkable to requirements.

**What already exists.** Most of this is standard SysML v2 and is already in the metamodel —
and, as of investigating this item, more of it is already in the _standard library content_
SysON ships than the plan originally assumed:

| Concept                          | Source                                                                                                                                                                                                                  |
| -------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Verification case                | `VerificationCaseDefinition`, `VerificationCaseUsage` (metamodel)                                                                                                                                                       |
| Analysis case                    | `AnalysisCaseDefinition`, `AnalysisCaseUsage` (metamodel)                                                                                                                                                               |
| Requirement→verification link    | `RequirementVerificationMembership` (metamodel)                                                                                                                                                                         |
| Requirement→component link       | `SatisfyRequirementUsage` (metamodel)                                                                                                                                                                                   |
| User-defined semantics           | `MetadataDefinition`, `MetadataUsage`, `MetadataFeature` (metamodel)                                                                                                                                                    |
| **Verification method enum**     | **`VerificationMethodKind`** (`test`/`demo`/`analyze`/`inspect`) — already defined in the standard library, `sysml.libraries/VerificationCases.json`                                                                    |
| **Verification method metadata** | **`VerificationMethod`** MetadataDefinition (attribute `kind : VerificationMethodKind`), documented as "_can be used as metadata annotating a verification case or action_" — already in the same standard library file |

`RequirementVerificationMembership` is the `verify` relationship. It is the link we want;
do not invent a parallel one. Likewise, `VerificationMethodKind`/`VerificationMethod` are
the standard verification-method enum and metadata; do not define a parallel ones. All 90
standard library files were checked for "procedure" — it does not exist anywhere. That is
the one genuine gap.

**What to build.** Much smaller than originally scoped, now that the enum/metadata exist:

1. **A small SysML model library** (`.sysml`) defining only what's missing: a
   `MetadataDefinition` carrying `procedureId`, restricted to `VerificationCaseUsage` (via
   `:> annotatedElement : VerificationCaseUsage;`), meant to be applied on a verification
   case usage _alongside_ the standard library's own `VerificationMethod` metadata rather
   than duplicating it.
2. **Library registration** so it loads as a standard library alongside the SysML ones.
   Resolved without any upstream Java edits: `StandardLibrariesLoader` already scans
   `classpath*:sysml.libraries/*.json` (via Spring's `classpath*:` aggregation across all
   jars), so shipping our own `sysml.libraries/<Name>.json` resource from a new module is
   enough — it loads as part of the existing "SysML Standard Library" family with zero
   changes to `SysONDefaultLibrariesConfiguration` or `SysONApplication`.
   **Status: done** — `backend/services/syson-verification-library`.
3. ~~Palette and tree entries so users can create verification cases without hand-typing.~~
   **Already done, no code needed.** Checked empirically via `rootObjectCreationDescriptions`:
   tree/root object creation is generic and metamodel-driven (`SysMLv2EditService`, 84
   creatable types), unlike diagrams. `Verification Case` and `Verification Case Definition`
   are already in that list. What's genuinely still missing is a **diagram palette** entry
   (checked `SDVNodeToolSectionSwitch`/`SDVDiagramDescriptionProvider` in
   `syson-standard-diagrams-view`: `AnalysisCaseDefinition`/`UseCaseDefinition` are wired
   in, `VerificationCase*` is not) — that's curated per-type in upstream diagram-view Java,
   not metamodel-driven, and editing it means touching upstream files. Deferred: not
   required by this item's acceptance criterion, which doesn't involve a diagram.
4. **A verification table view** (see item 2 — same pattern as the requirements table,
   new Maven module `backend/views/syson-table-verification-view`). **Status: done**, with
   caveats:
   - Columns: DeclaredName, Verification Method, Procedure ID. Flat (no nesting, unlike the
     requirements table — verification cases don't need it here).
   - The Verification Method cell is a **text field, not a dropdown**: as of Sirius Web
     `2026.7.4`, `TableBuilders` only offers textfield/label/textarea cell widgets. Editing
     accepts the literal (`test`/`demo`/`analyze`/`inspect`) or the display label
     (`Test`/`Demonstration`/`Analysis`/`Inspection`), case-insensitively; anything else is
     rejected rather than silently accepted.
   - Cell edits re-import a small textual SysML fragment (e.g.
     `@VerificationMethod { kind = VerificationMethodKind::test; }`) via the same
     `ISysMLTextImporter` every other textual edit in the app goes through, rather than
     hand-building the `MetadataUsage`/`ReferenceUsage`/`FeatureValue` EMF structure
     directly — same "let the parser validate it" principle as item 4's write path, applied
     at cell-edit scale.
   - Verified: unit tests parse real textual SysML (through the same `syside-cli` pipeline
     the app uses) and confirm the query services read both metadata kinds back correctly;
     full reactor build passes; the app boots cleanly with the view registered. **Not**
     verified: interactively opening the table in a browser and editing a cell — stopped
     short of a full GraphQL-driven round trip (create project → find root object →
     create representation → edit cell) since it isn't a small check, and the read/write
     logic is already covered at the unit level. Worth doing before calling this
     production-ready.
   - Row creation ("Create verification case" tool) mirrors the requirements table's
     pattern; unlike it, there's no "import existing" tool.

**Explicitly out of scope:** adding classes to `backend/metamodel`; redefining
`VerificationMethodKind`/`VerificationMethod`.

**Acceptance:** a user can create a verification case, set its method (via the existing
standard `VerificationMethod` metadata) and procedure ID (via our new metadata), link it to
a requirement, export the model to textual SysML v2, and reimport it without loss.

---

## 2. Operational concept objects

**Goal:** A mostly text-based object describing users and how they intend to use the system.

**Status: done**, prototyping the "documented package" option — it was enough. Turned out
to need much less new code than the plan expected, similarly to item 1: most of "what to
build" below was already present once checked.

**What already exists.** `UseCaseDefinition`, `UseCaseUsage`, `IncludeUseCaseUsage`,
`ConcernDefinition`, `ConcernUsage`, `FramedConcernMembership`, `ActorMembership`,
`StakeholderMembership` (all metamodel). Documentation bodies are already supported and
already surfaced two ways, not just one: the requirements table reads them via
`getDocumentationBody()`, **and** — checked while scoping this item —
`SysMLv2PropertiesConfigurer.createDocumentationWidget()` already puts an editable
Documentation text area in the standard Details/Properties panel for _any_ element (gated
only on "not an `AnnotatingElement`"), so narrative authoring already worked, generically,
before this item started. It's a plain textarea, not rendered markdown, but it's real
multi-line text editing, matching the "mostly text-based" framing. The frontend's
`@eclipse-sirius/sirius-components-markdown` dependency turned out to be unused by any
existing SysML representation; the platform does have a `RichTextDescription` form widget
available if this is ever upgraded from textarea to real markdown (not done — see below).
Tree/root object creation is generic and metamodel-driven (established in item 1), so
`UseCaseUsage`/`PartUsage` are already creatable without new code.

**What was actually built** — new module `backend/services/syson-operational-concept-library`:

1. **The model pattern needs no new modeling concepts.** An operational concept is simply
   a `Package` containing `UseCaseUsage`s, actor/stakeholder `PartUsage`s, and a
   `Documentation` — all already-valid SysML v2, composed freely, no new relationship or
   containment rule required.
2. **A marker `MetadataDefinition`** (`OperationalConcept`, no attributes) so such a package
   is identifiable. Unlike `VerificationProcedure` in item 1, this is **unrestricted** —
   checked every `sysml.libraries/*.json` and `kerml.libraries/*.json` file and confirmed
   `Package`/`Namespace` are not reflectively exposed as metaclasses anywhere (unlike
   `VerificationCaseUsage`), so there is no `SysML::Package` to restrict `annotatedElement`
   against. Loads the same zero-upstream-edit way as item 1's library.
3. **No new form or markdown representation was built.** Given the existing generic
   Documentation textarea already satisfies "a user can author an operational concept
   narrative" (the acceptance criterion), building a bespoke `RichTextDescription`-based
   form — for which there was no existing "add a form" pattern in this codebase to mirror,
   unlike tables — was judged disproportionate to what it would add. Upgrading the
   narrative-editing experience from textarea to real rendered markdown remains a clearly
   separable future enhancement, not required now.
4. **Explorer integration: icon done, filter and creation tool deferred.**
   - Icon: `OperationalConceptLabelServiceDelegate implements ILabelServiceDelegate` — a
     genuine Sirius Web chain-of-responsibility extension point (`canHandle`/decline),
     already used by `SysMLv2LabelService` itself and by
     `SysONSelectionDialogFragmentLabelService`. A second additive `@Service` bean opts in
     only for `OperationalConcept`-tagged elements; zero upstream edits. Reuses the
     `UseCaseDefinition` icon asset (no dedicated icon exists).
   - Filter (e.g. "Hide Operational Concepts"): deferred. `ITreeFilterProvider` is
     similarly pluggable, but the actual filter _logic_ lives in
     `ISysONExplorerFilterService`, which dispatches a fixed, upstream-owned set of filter
     ids rather than accepting pluggable filter implementations — adding a new one means
     editing that upstream service.
   - One-step "New Operational Concept" creation tool: deferred. Unlike tables
     (`IToolMenuEntryExecutor`), the tree explorer has no per-type Java creation-tool hook;
     creation today is create a `Package` via the generic tool, then type
     `@OperationalConcept;` (and any starter use cases/actors/documentation) via the
     existing textual-insert capability. Revisit if this friction matters in practice.

**Acceptance:** a user can author an operational concept narrative (via the existing
generic Documentation panel), reference use cases from it (already-standard SysML
composition), and see it distinguished in the explorer (new icon delegate) — all satisfied.

---

## 3. Requirements table

**Goal:** Add requirements by adding a row; add columns, including one mapping to a
verification procedure ID.

**Current state.** `backend/views/syson-table-requirements-view`, package
`org.eclipse.syson.table.requirements.view`, 528 lines across 10 files.
`RTVTableDescriptionProvider.java` (198 lines) defines exactly three columns:

- `RTV-ColumnDescription-DeclaredName` → `aql:self.declaredName`
- `RTV-ColumnDescription-ReqId` → `aql:self.reqId`
- `RTV-ColumnDescription-Documentation` → `aql:self.getDocumentationBody()`

Each is a `ColumnDescription` (name, `semanticCandidatesExpression`, `headerLabelExpression`,
width, resizable, sortable) paired with a `CellDescription` (a `preconditionExpression` of
the form `aql:columnTargetObject == 'ReqId'`, a `valueExpression`, and an edit handler
taking `aql:newValue`).

Row creation **already works** via `RTVTableCreateRequirementToolExecutor`
(`IToolMenuEntryExecutor`) calling `RTVMutationServices.createRequirement`. There is also
`RTVTableImportExistingRequirementsToolExecutor`. Context menu entries exist for delete-from-
table, delete-from-model, and new nested requirement.

**Task 3a — verification procedure ID column. Done, via a project-owned workaround
(Path A) for a confirmed upstream metamodel gap.**

Attempted this exactly as scoped: walk from a `RequirementUsage` to the
`VerificationCaseUsage` that verifies it, then read `procedureId` via item 1's
`VTVQueryServices.getProcedureId()` (reused directly rather than duplicated). Found there
is no reverse reference (`VerificationCaseUsage.getVerifiedRequirement()` is forward-only),
so the plan was to scan the resource set for a `VerificationCaseUsage` whose
`getVerifiedRequirement()` contains the target requirement.

That accessor does not work. Checked the generated (do-not-edit) metamodel directly:

- `VerificationCaseUsageImpl.getVerifiedRequirement()` unconditionally returns a new empty
  list — not computed from anything.
- `RequirementVerificationMembershipImpl.basicGetOwnedRequirement()` and
  `.basicGetVerifiedRequirement()` are both literal `// TODO: implement this method`
  stubs returning `null` unconditionally.
- Even `getReferencedConstraint()`, a hand-written (`@generated NOT`) override — meaning a
  human touched this specific method, not the generator — just circularly calls the
  still-broken `getVerifiedRequirement()`.

Separately, and initially mistaken for the cause before finding the above: the textual
`verify req1;` statement (inside a `verification vc1 : VerificationCases::VerificationCase`
body) parses with zero errors/reports via the bundled `syside-cli`, but produces no
`RequirementVerificationMembership`/`ObjectiveMembership` structure at all — so even the
_write_ path (however it's eventually built) cannot be authored textually today either.
Root-caused to the metamodel stub above, not a syside-cli gap.

This is a hard blocker per CLAUDE.md's constraint against modifying `backend/metamodel` —
the standard "verify" relationship genuinely cannot be read back, and there is no way to fix
that without editing generated code. Rather than ship a column that's silently always blank,
or wait on an upstream fix with no ETA, built a project-owned substitute for the broken
relationship instead of the relationship itself:

- **`VerificationLink`** — a new `MetadataDefinition` in `syson-verification-library`
  (`VerificationProcedureLibrary`), applied on the `RequirementUsage` being verified (not on
  the case, unlike the standard relationship) with a `ref verificationCase` feature pointing
  at the `VerificationCaseUsage` that verifies it. Its doc comment records exactly why it
  exists (the metamodel evidence above) and says to retire it once the upstream accessors
  are fixed. This is genuinely non-standard — a SysML v2 tool other than this fork won't
  understand `VerificationLink`, unlike everything else built for items 1–2 — so it is a
  deliberate, documented trade-off, not a free win.
- **Read**: `VerificationLinkQueryServices.getLinkedVerificationCase(RequirementUsage)`
  (new, in `syson-verification-library`) walks the applied metadata's
  `FeatureReferenceExpression` to the linked case; `RTVQueryServices.getVerificationProcedureId`
  composes that with item 1's `VTVQueryServices.getProcedureId()`.
- **Write**: `RTVMutationServices.setVerificationLink` — the column is edited by typing the
  verification case's declared name (not the procedure ID itself, which isn't
  independently settable from this cell). Matches by exact `declaredName` across the whole
  resource set; zero or more than one match rejects the edit rather than guessing or linking
  to the wrong case. A blank value clears an existing link. Applies/removes the metadata via
  the same `ISysMLTextImporter` round trip every other textual edit in this codebase uses,
  so the parser validates the written structure.
- Column and cell wired into `RTVTableDescriptionProvider` as an editable textfield, same
  shape as `Documentation`.
- Verified: `VerificationLinkQueryServicesTests` (3 tests) and `RTVVerificationLinkTests` (4
  round-trip tests, through the real textual-import pipeline) all pass; full reactor build
  (`syson-verification-library`, `syson-table-verification-view`,
  `syson-table-requirements-view`, and their dependents) is green, 20/20 tests in the
  requirements-view module; `ServiceClassInstantiationTests` confirms `RTVQueryServices` and
  `RTVMutationServices`'s new constructors resolve the way Sirius Web actually instantiates
  service classes at runtime, not just via hand-called `new` in unit tests; the backend boots
  cleanly end to end with the new library and both view modules loaded. **Not** verified:
  interactively editing the cell in a browser (same caveat as item 1's table).

**Retire this when upstream fixes the real bug.** `VerificationLink` is explicitly a
stand-in, not a permanent design decision — if a future SysON/metamodel release implements
`RequirementVerificationMembership`'s accessors correctly, migrate this column to read the
standard relationship and drop `VerificationLink` (a one-time model migration for anyone who
used it in the meantime).

**Positive side effect: caught a real bug in item 1 along the way.** Investigating this,
built a diagnostic (`ServiceClassInstantiationTests` in `syson-application`) that
instantiates every custom AQL "service class" the way Sirius Web actually does at runtime
(`AutowireCapableBeanFactory.createBean(Class)`, confirmed by decompiling
`ViewAQLInterpreterFactory`), rather than by hand-calling `new` as unit tests do. It caught
`VTVQueryServices` (item 1's Verification Table View) taking `MetamodelQueryElementService`
— a plain utility class, not a Spring bean — as a constructor parameter: compiled fine,
passed its own unit tests, and would have thrown `UnsatisfiedDependencyException` the
moment a user opened that table. Fixed (construct it internally with `new` instead of
injecting it) and verified. Keep adding new service classes to this test as new
tables/forms are built — it is exactly the kind of "fails at runtime, not compile time"
gotcha CLAUDE.md already warns about for AQL expressions, one level down the call stack.

**Task 3b — four fixed columns. Done.**
Same `ColumnDescription`/`CellDescription` pattern as 3a, added to the same
`RTVTableDescriptionProvider` (split into `createBaseCells()`/`createRequirementAttributeCells()`
to stay under the checkstyle method-length limit), with query/mutation methods added to the
existing `RTVQueryServices`/`RTVMutationServices`. All four are plain textfield cells: as of
Sirius Web `2026.7.4` there is still no dropdown/select `CellDescription` widget (same
constraint item 1 already hit for Verification Method), so "dropdown" columns accept typed
input matched against known values instead.

- **Parent Requirements — done.** Multi-valued: entering a comma-separated list of parent
  reqIds links to those parent requirements; the cell displays the current parents' reqIds,
  comma-separated. Confirmed via the metamodel and a scratch test that the mechanism is the
  generic `Dependency` (`client`/`supplier`), with this requirement as `client` and each
  resolved parent as `supplier` — textually authored as `dependency from <this> to <parent>;`.
  Any reqId that fails to resolve to exactly one other requirement rejects the whole edit
  (same "reject rather than guess" convention as 3a's link column). One real gotcha found and
  fixed: `ISysMLTextImporter` places a single-relationship snippet like this **directly** in
  `ownedRelationship`, whereas the same statement written inline during a full package parse
  gets wrapped in an `OwningMembership` — relying on only one shape silently found (or
  deleted) nothing for the other, so the read and delete logic now check both.
- **Requirement Type — done, using existing standard library content, no metadata of our
  own.** Checked `Requirements.json` before building anything: it already ships
  `Requirements::FunctionalRequirementCheck`, `InterfaceRequirementCheck`,
  `PerformanceRequirementCheck`, `PhysicalRequirementCheck`, and `DesignConstraintCheck` —
  each a documented `RequirementDefinition` meant to be specialized, i.e. exactly the
  standard SysML v2 way to classify a requirement's type. A `RequirementUsage` is classified
  by adding one of these as an additional type alongside its own (usages support multiple
  typings via a comma-separated list — `requirement req1 : R1, Requirements:: FunctionalRequirementCheck;` — confirmed via a scratch test dumping `getType()`). Reading
  uses `Type.specializesFromLibrary(String)`, an existing hand-written method this codebase's
  own SysML validation rules already rely on, so a requirement whose own `requirement def`
  specializes one of these indirectly is recognized too, not just a direct typing on the
  usage. Writing is the one cell in this module that is **not** a textual reimport: this
  grammar has no standalone syntax for adding a typing relationship after the fact (checked
  `SysMLv2Keywords` — no `typing` keyword), so `RTVMutationServices.setRequirementType`
  builds the `FeatureTyping` directly with the EMF factory, mirroring an existing pattern in
  `DetailsViewService.handleFeatureTypingNewValue`.
- **Threshold or Objective — done**, via a new metadata `ThresholdOrObjective` (attribute
  `kind : ThresholdOrObjectiveKind`) — checked first and confirmed neither concept exists
  anywhere in the standard library, unlike Requirement Type. New module
  `backend/services/syson-requirements-library`, same "SysML model library, loads via the
  classpath-scan trick, zero upstream edits" pattern as items 1 and 2's libraries. One real
  gotcha found and fixed: `objective` is a reserved keyword in this grammar
  (`SysMLv2Keywords.OBJECTIVE`) — an unescaped `enum objective;` (or a reference to it)
  silently truncates the rest of the file's/snippet's parse with no reported error. Fixed by
  escaping it with single quotes (`enum 'objective';`, `ThresholdOrObjectiveKind::'objective'`)
  everywhere it's declared or referenced, confirmed via a scratch test dumping the raw parse.
- **Category — done**, via a new metadata `RequirementCategory` (attribute `category : String`) in the same new library, same shape as `VerificationProcedure` from item 1.
  **A second, more serious gotcha found and fixed, affecting item 1 too:** this grammar's
  string literals have **no working escape for an embedded quote at all** — neither
  backslash-escaping nor doubling the quote (the SQL/Pascal convention) actually round-trips
  the value; both silently truncate the string at the first quote, and doubling doesn't even
  report a warning, unlike most of this session's other silent-failure findings. Confirmed
  empirically with a scratch test comparing the written snippet against the read-back value
  before trusting either approach. Fixed by rejecting (not attempting to escape) any Category
  or Procedure ID value containing a `"` — **and backported the same fix to item 1's
  `VTVMutationServices.setProcedureId`**, which had the identical latent bug (it had shipped
  with the backslash-escaping version, untested against an actual embedded quote). Backslashes
  need no escaping and round-trip correctly as-is.

**Verified:** unit tests for all four columns (round-tripping through the real textual-import
/ EMF-mutation pipeline, as established for 3a) plus the new library's own loading/query
tests — 38/38 in `syson-table-requirements-view`, 18/18 in `syson-table-verification-view`,
22/22 in the new `syson-requirements-library`; full reactor build green;
`ServiceClassInstantiationTests` passes for the unchanged `RTVQueryServices`/
`RTVMutationServices` classes; the backend boots cleanly with the new library and all four
columns registered. **Not** verified: interactively editing a cell in a browser (same
standing caveat as items 1 and 3a).

**Task 3c — automatic requirement ID assignment. Done.**
`RTVMutationServices.createRequirement` now assigns `reqId` automatically (e.g. `REQ-001`)
instead of leaving it blank; the existing "New Nested Requirement" path
(`createNestedRequirement`) gets one too, sharing the same sequence as top-level rows so a
table's requirement IDs stay in one series regardless of nesting.

- **Storage: a new metadata, `RequirementIdSequence`** (attributes `prefix : String`,
  `nextNumber : Integer`) in `syson-requirements-library`, applied on the requirements
  table's own owning namespace (the `ViewUsage`'s `getOwningNamespace()`) — not on individual
  requirements, and not restricted to a reflective metaclass, for the same
  Package/Namespace-isn't-reflectively-exposed reason as `OperationalConcept` (item 2) and
  the new `RequirementIdSequence` unrestricted definition. This satisfies the "exportable and
  versioned along with the rest of the model" requirement from this bullet's original
  wording: it's ordinary applied metadata, no separate config store.
- **Not a scan-based scheme.** `nextNumber` is a monotonically-increasing counter,
  incremented every assignment and never derived from scanning existing reqIds — deleting
  the highest-numbered requirement does not free its number for reuse, satisfying the
  "doesn't collide or reuse numbers" requirement. Verified with a dedicated test that creates
  a requirement, deletes it, creates another, and confirms the number still advances.
- **Increments via direct EMF mutation, not textual reimport** — the one case in this
  codebase's metadata-handling where that's deliberate rather than a workaround: incrementing
  is a single `LiteralInteger.setValue(int)` flip on an object already resolved by
  `RequirementIdSequenceQueryServices.getNextNumberLiteral`, and re-deriving the metadata's
  full redefinition structure through a reimport for that would be needless overhead. Only
  the one-time "no sequence exists yet" case creates the metadata via the usual textual
  reimport, consistent with every other metadata application in this module.
- **Prefix is user-settable, but through the model, not a settings UI.** Defaults to
  `REQ-` starting at 1 if no `RequirementIdSequence` is applied yet. A different prefix (e.g.
  `SYS-`) is set by hand-applying `@RequirementAttributesLibrary::RequirementIdSequence { prefix = "SYS-"; nextNumber = 1; }` on the table's owning package before the first
  requirement is created through it — no dedicated "ID prefix" dialog or column was built.
  This is a deliberate scope call, consistent with this project's minimal-new-UI stance
  elsewhere (item 2's deferred creation tool/filter): the acceptance criterion only requires
  the prefix be settable and exportable, not that there be bespoke UI for it. Revisit if this
  friction matters in practice.
- **The existing 3a/3b edit handlers still work unchanged** — auto-assignment only sets the
  initial value; the ReqId column's plain `SetValue` (and every other column's edit handler)
  is untouched, confirmed by a dedicated "manual override after auto-assignment" test.

**Verified:** `RTVAutoRequirementIdTests` (4 tests: sequential default-prefix assignment,
pre-existing custom prefix/starting number, no-reuse-after-deletion, manual override) and the
library's own `RequirementIdSequenceQueryServicesTests` (3 tests, including the in-place
increment) all pass — 42/42 in `syson-table-requirements-view`, 26/26 in
`syson-requirements-library`; full reactor build green; backend boots cleanly.

**On the old "settle this design before writing code" note above:** it no longer applies
as written. It was scoped to the previous version of 3b — arbitrary, user-configurable
columns added at runtime and bound to arbitrary model attributes — which raised real
questions about in-model vs. out-of-model config and export semantics. The current 3b/3c
are a fixed, developer-defined set of columns and a fixed ID scheme, closer in shape to 3a,
so those specific questions don't apply. The narrower design decisions that remain (which
metamodel relationship backs Parent Requirements, where enum values live, where the ID
prefix lives) are called out inline above instead. If runtime user-configurable columns are
still wanted later, that open question should come back as its own task rather than being
folded into 3b.

**Acceptance:** a user can add a requirement row with an auto-assigned, prefixed ID; edit
its verification procedure ID; link it to one or more parent requirements; set Threshold or
Objective and Requirement Type from dropdowns; enter free-text Category; and have all of it
survive export to textual SysML v2 and reimport without loss.

**Status:** 3a, 3b, and 3c all done — item 3 is complete. 3a via the `VerificationLink`
workaround (the export/reimport half of acceptance holds for it specifically, since
`VerificationLink` is real applied metadata that round-trips through
`ISysMLTextImporter`/`syson-sysml-export` like any other metadata, just not interoperable
with other SysML v2 tools). 3b's four columns and 3c's ID sequence are all real SysML v2
structure (a `Dependency` relationship, a standard-library specialization, and three small
metadata definitions), so the export/reimport half of acceptance holds for all of them
without caveat — not verified end-to-end through an actual export/reimport round trip yet,
but each underlying structure is exactly what `syson-sysml-export` already knows how to
serialize (relationships, typings, and metadata usages are not special-cased). Not verified
anywhere in item 3: interactively exercising the table in a browser (standing caveat, called
out per-task above) and an actual export→reimport round trip through the app rather than at
the unit level.

**Correction (2026-09-07): the "full reactor build green" claims above were wrong, and item 3
shipped red.** Those runs covered the view and library modules only. They never ran
`syson-application`'s integration tests, which is where the requirements table is exercised
end to end — and two of them
(`RequirementsTableControllerIntegrationTests.givenRequirementsViewTableDescription...` and
`givenNewRequirementsViewTableDescription...`) were failing on `Expected size: 3 but was: 8`
from the moment 3a/3b added columns. The failure was correct: the assertions encoded
upstream's three-column table. They now assert this fork's eight columns, via a named
constant explaining why the expectation deliberately diverges from upstream.

**The lesson, which applies to every future item:** module-level tests passing is not the
same as the build being green. A change to a representation description is only really
exercised by the integration tests in `syson-application`, which are slow enough to be easy
to skip and are not run by building the module you edited. Run them before claiming a
feature is done.

---

## 4. AI modeling assistant

**Goal:** Create an agent or multiple agents that can help with or automate the following tasks including encoding these outputs in SysML v2 data structures:

1. Drafting concepts of operation
2. Building use cases
3. Deriving system-level requirements
4. Defining high level-architectures using SysML v2 General and Interconnection Views
5. Deriving subsystem and component requirements
6. Defining classes, class functions and software messages
7. Designing verification procedures and linking them to requirements

**Current state.** No code. No LLM integration anywhere in the tree. What exists so far is
scoping research (2026-09-06), recorded below.

**Research findings (2026-09-06).** All checked against the tree or the real parse pipeline,
not assumed:

1. **The textual write path reaches diagrams, so goal 4 needs no diagram-specific write
   API.** SysON diagrams render only elements explicitly *exposed*: every node tool calls
   `DiagramMutationExposeService.expose`, which stores a `MembershipExpose` on the
   `ViewUsage` (`DiagramMutationExposeService.java:139`). `expose` is a real SysML v2
   keyword (`SysMLv2Keywords:199`) and `SysMLElementSerializer` writes it (line 2386), so an
   agent can emit a `ViewUsage` with `expose` statements textually and get a populated
   General or Interconnection View. Layout is not in the semantic model — Sirius Web owns
   node positions — so the agent controls diagram *content*, not arrangement.
   **The failure mode to design against:** create elements without exposing them and the
   user opens a diagram to find it empty, which reads as a broken feature rather than a
   missing statement.
2. **Goal 6 is text-only and the mapping is verified.** A software-flavoured model
   (`port def`, `part def` with `attribute` + visibility, `port`, `action`, `flow`) parses
   with zero reports and survives a two-generation round trip (parse → serialize → reparse)
   with metaclasses intact. Mapping: class → `part def` (or `item def` for pure data);
   field → `attribute`, with `private`/`protected`/`public` honoured; method → `action`;
   message → `flow`. `message x from a to b;` is also accepted and produces a `FlowUsage`,
   serialized back as `flow`. `doc /* ... */` bodies round-trip inline, so structure and
   narrative come from one generation pass. Note there is **no `class`/`struct`/`datatype`
   keyword** in SysML v2 concrete syntax — `Class`/`Structure`/`DataType` exist only as
   KerML-layer metaclasses and are not textually authorable — and no `Message` metaclass
   at all.
3. **`calc` is silently lossy — avoid emitting it.** `calc name : Type;` parses correctly as
   a `CalculationUsage`, then serializes as `action` and reparses as an `ActionUsage`:
   `SysMLElementSerializer` has no `caseCalculationUsage` and falls through to
   `caseActionUsage`. Zero parse reports at every step, same silent-failure signature as the
   reserved `objective` keyword and the unescapable quote from item 3. Emit
   `action name : ReturnType` instead. This is an upstream serializer gap, not ours, and it
   means the cross-cutting "export/reimport is lossless" check fails today for any model
   using `calc`, independent of this item. Worth reporting upstream.

**Decisions taken (2026-09-07).**

- **The pipeline is two-phase, and that is forced by the platform, not a preference.** Every
  GraphQL mutation is dispatched through `IEditingContextDispatcher` to the project's
  `EditingContextEventProcessor`, which holds a single `ExecutorService` — and
  `SiriusWebStarterConfiguration` builds it with `Executors.newSingleThreadExecutor()`
  (confirmed in the bytecode; no sources jar ships). An LLM call inside a mutation handler
  would therefore block *every* edit to that project, for every user, for the length of the
  call. So: a fast dispatched read to serialize the scope, the LLM call off that thread
  entirely, then a fast dispatched mutation to apply accepted items. The proposal has to
  live somewhere between those phases.
- **Proposals are persisted** (chosen over in-memory or in-model). An expensive generation
  survives a refresh or restart, a proposal can be reviewed by someone other than its
  requester, and unattended generation becomes additive rather than a rewrite. In-model
  storage was rejected because unapproved content would live in the model and item 2 already
  established that the explorer's filter extension point is not pluggable, so hiding it
  would mean upstream edits.
- **This settles the "or automate" tension.** Automate the mechanics; keep the merge gated.
  The review gate is a property of the schema (nothing is applied until items are accepted
  one by one), not of a particular UI.
- **Inference goes to the Anthropic API**, official Java SDK (`com.anthropic:anthropic-java`),
  default model `claude-opus-5`, credentials via Spring properties and environment variables.
- **Acceptance stays scoped to one slice** — goal 3 only, rather than expanding to all seven.

**Built so far — `backend/services/syson-ai-services` (persistence slice only).**

The proposal store: `ai_proposal` + `ai_proposal_item`, per-item status so individual
accept/reject is a property of the data rather than of the UI, and prompt/model/author/
timestamps as the provenance that must survive onto generated elements. Verified against a
real PostgreSQL (Testcontainers), not a substitute: the changelog applies, the Spring Data
JDBC mapping matches it, the child collection round-trips, and per-item decisions drive the
proposal's derived status.

Two platform integration traps found and handled, both of which fail silently at runtime:

1. **Never publish a `SpringLiquibase` bean.** Boot's
   `LiquibaseAutoConfiguration$LiquibaseConfiguration` is
   `@ConditionalOnMissingBean(SpringLiquibase.class)`, so declaring one of our own would
   switch off the platform's migration and the entire Sirius Web schema would stop being
   created — first visible as a missing table at runtime, long after our own tables looked
   fine. `SysONAiDatabaseMigration` constructs one locally instead and drives it, ordering
   itself after the platform's via an `ObjectProvider`. `SysONAiSchemaCoexistenceTests`
   asserts both schemas and both changelog tracking tables exist, so a future "simplification"
   fails loudly.
   The platform's own `db/db.changelog-master.xml` (inside `sirius-web-infrastructure`) is a
   fixed `<include>` list with no `includeAll` and no extension hook, which is why we run a
   separate changelog rather than contributing to theirs.
2. **`@EnableJdbcRepositories` is scoped to `org.eclipse.sirius.web.domain`**, so our
   repositories need their own. Unlike the Liquibase case this is purely additive.

One more gotcha worth knowing: the shared `@SpringJUnitConfig` context behind
`AbstractIntegrationTests` never loads `application.properties`, so
`spring.liquibase.change-log` is unset there and Boot's Liquibase falls back to a default
path that does not exist. Anything touching the datasource has to be a full
`@SpringBootTest(webEnvironment = RANDOM_PORT)` — the recipe already used by
`SysMLMoveElementServiceDelegateIntegrationTests`.

**Built next — model-scope serialization (the read path).**

`ModelScopeSelector` expands a user selection into the elements a generation will see;
`ModelScopeSerializer` renders them as textual SysML v2 for the prompt. `ModelScopePolicy`
carries the limits — owned depth, related depth, an element budget and a character budget —
so scope control is a parameter the caller sets rather than a surprise on an invoice.

Three decisions worth keeping:

1. **Expansion walks owned relationships, not `eCrossReferences()`.** A typing such as
   `part x : Def` is owned by `x` as a `FeatureTyping` whose other end is `Def`, so walking
   `getOwnedRelationship()` reaches definitions without depending on how EMF treats derived
   references. Verified against a parsed model rather than assumed.
2. **Standard-library elements are never pulled in**, via `ElementUtil.isFromStandardLibrary`.
   This filter does real work rather than guarding a case that cannot happen: an
   `attribute capacity : ScalarValues::Real` genuinely reaches `ScalarValues::Real` in one
   hop, and without the filter the library would flood the context.
3. **The output is fragments under qualified-name comments, not one compilable file.** A
   scope is an arbitrary slice, so its elements generally share no enclosing namespace, and
   pretending otherwise would mean inventing package structure that is not in the model.
   Truncation is reported rather than silent — a model shown a partial model without being
   told will reason confidently about what it cannot see.

**A test-classpath gotcha that will bite again.** A module cannot casually hold both a Spring
Boot test and a test-scoped dependency on `syson-application-configuration`. That dependency
is what lets a test parse against the real default libraries, but it puts upstream's entire
web stack on the classpath, and Boot then insists on standing it up: `WebEnvironment.NONE`
leaves `SpringWebMvcConfigurer` without `WebProperties`, `MOCK` leaves `WebSocketConfiguration`
without a real `ServerContainer`, and either way `ImagesController`'s `/api/images/**/*`
mapping is rejected by Spring 7's pattern parser unless
`spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER` is set — which the real application
does set in `application.properties`, a file not on a service module's classpath. The way
through is `webEnvironment = RANDOM_PORT` plus that property, which is the same recipe
`SysONAiSchemaCoexistenceTests` already needed.

**Built next — the Anthropic client and goal 3's prompt.**

`SysONAiProperties` (`syson.ai.*`), `AnthropicGenerationClient` over the official
`com.anthropic:anthropic-java` SDK, and `DerivedRequirementsPromptFactory`, the first of the
thin per-goal adapters.

- **No key means switched off, not broken.** `syson.ai.api-key` is wired to
  `${ANTHROPIC_API_KEY:}` in `application.properties` and defaults to empty; the client reports
  a `NOT_CONFIGURED` outcome rather than throwing, so the whole tool builds, boots and tests
  without credentials. The key is never written to a file in the repo.
- **A refusal is not an exception.** The API returns HTTP 200 with `stop_reason: refusal`, and
  a caller that only catches exceptions would read that as an empty answer. `GenerationResult`
  models `ANSWERED` / `REFUSED` / `TRUNCATED` / `NOT_CONFIGURED` explicitly. `TRUNCATED`
  matters just as much: a response cut off at the token ceiling is a fragment, and parsing it
  would silently drop whatever was lost.
- **The prompt asks for textual SysML v2, not JSON.** The response is validated by re-parsing
  it through the same importer every other textual edit uses, so the parser is the check — which
  only works if the model emits something the parser reads. The system prompt also forbids
  `calc`, the construct recorded above as silently lossy, and tells the model when it is seeing
  a truncated scope.

Model is `claude-opus-5`. Still to do on this piece: **server-side refusal fallbacks** are
recommended for Opus 5 code and are not wired up — the Java builder methods are not in the
reference used here, and guessing SDK bindings is how you get code that compiles and fails at
runtime. Worth adding from the SDK's own examples before this goes in front of users.

**Built next — response validation, and a trap worth knowing about.**

`GeneratedSysmlValidator` checks a model's response before any of it reaches a reviewer, and
splits it into individually reviewable candidates.

**Parsing alone is not a sufficient check, which is not obvious.** SysML v2 treats a bare name
as an element declaration, so ordinary English prose parses *cleanly*: "Sure! Here are some
requirements you might consider adopting." yields **nine** well-formed elements named `Sure`,
`Here`, `are`, `some`… with **zero parse errors and zero warnings**. Measured, not assumed. A
parse-only validator — which is what "let the parser validate it" naturally suggests, and what
was built first here — would hand all nine to a reviewer as candidate requirements. So every
top-level element must also be an instance of the kind the caller asked for
(`RequirementUsage` for goal 3); that type check, not the parse, is what actually rejects prose.
A response that is *partly* prose fails rather than being quietly trimmed: partly-prose means
regenerate, not silently keep the good half.

Two other decisions:

- **Validation does not use `ISysMLTextImporter`.** That interface parses *and integrates* into
  a live editing context — it is the write path, and validation happens before a human has
  approved anything. So it parses into a throwaway `ResourceSet` the project never sees. The
  importer is the right tool at apply time, on accepted candidates only. The trade: references
  from generated text into the project or the libraries do not resolve during validation, so
  this checks well-formedness, not that every name exists. Name resolution is the importer's job.
- **The stored fragment is the re-serialized element, not the model's own text**, so what the
  reviewer reads, what is stored on the proposal item, and what is applied are the same string.
  Markdown fences are stripped rather than treated as fatal — models emit them despite being
  told not to — but the fact that it happened is recorded, since it means the prompt was ignored.

**Built next — the pipeline itself.**

`DerivedRequirementsPipeline` runs scope → serialize → prompt → generate → validate → persist,
and is what the GraphQL mutations will sit on. Two properties are worth stating plainly because
they are what make the design safe rather than merely intended:

- **It has no code path into the model.** It reads elements to build context and writes a
  proposal row; it never adds an element. That is what the single-threaded
  `EditingContextEventProcessor` forces — seeds are read on that thread and handed in, the LLM
  call happens off it, and applying an accepted candidate is a separate fast mutation later.
- **The review gate is in the data.** Everything it writes is `PENDING_REVIEW` with every item
  `PENDING` and no applied element id. Tests assert this rather than trusting it.

Nothing is persisted when a run fails — a refusal, a truncated response, or output that fails
validation all leave no reviewable artefact, because storing candidates that failed validation
would invite someone to apply them later. A model that correctly proposes nothing is reported
as "nothing to review", not as a failure.

**Built next — provenance, and applying an accepted candidate.**

`AiProvenance.sysml` ships a `Generated` metadata definition the same way items 1–3 ship theirs;
`ProposalApplyService` creates the element and stamps it.

**Two silent-failure findings, both measured.**

1. **`by`, `at` and `when` cannot be used as attribute names.** plan.md sketched
   `@Generated { by, prompt, at }`. Two of those three are reserved, and an attribute using one
   is dropped by the parser **with zero reports** — *along with every attribute declared after
   it in the same body*. The first version of this library therefore defined a `Generated`
   metadata definition with **no attributes at all** while every parse report stayed empty.
   Probed name by name: `by`, `at`, `when` vanish; `proposalId`, `generatedBy`, `generatedAt`,
   `who` survive. `AiProvenanceLibraryLoadingTest` now asserts all three attributes are present,
   so a future rename to something shorter fails loudly instead of quietly emptying provenance.
2. **Only one of three annotation syntaxes both attaches and carries values**, and all three
   parse without errors. Written at the same level as the declaration, `@Name { ... }` becomes a
   **sibling** rather than an annotation. Written as prefix metadata, `#Name` attaches but
   carries no values. Written unqualified, the type silently fails to resolve. What works is
   `@AiProvenanceLibrary::Generated { ... }` imported *with the new element as its parent* —
   the same shape every other metadata edit in this application uses.

**The prompt is not stamped on the element.** Free text would have to be embedded as a SysML
string literal, and a quotation mark inside one cannot be escaped (item 3). The annotation
carries `proposalId` instead — a pointer to the row holding the prompt, the model, the author
and every other candidate from that run, which is both safer and more complete.

**An element never stays in the model unstamped.** Attaching provenance needs a second import,
so if it fails the element is deleted again and the item is not marked applied. Leaving a
generated element behind without provenance is the exact failure the library exists to prevent.

**Built next — the GraphQL surface.**

Two mutations, `generateDerivedRequirements` and `applyAiProposalItem`, plus the handlers behind
them. The generate fetcher is where the two-phase design actually lives: the client sees one
mutation, but reading the model is dispatched to the editing context (fast, and the only part
that must happen there) while the model call runs on a bounded-elastic scheduler off that thread.
`DerivedRequirementsPipeline` was refactored to take an already-serialized scope rather than
model elements, so the threading rule is now structural — there is no EMF object in reach of the
model call, so it cannot accidentally end up back on the single-threaded executor.

Three things this cost, each a silent failure that booting the application does not catch:

1. **A `.graphqls` file that is not picked up costs nothing at startup.** The application comes
   up perfectly happily and the mutation simply does not exist, which a client discovers the
   first time a user clicks the button. `SysONAiGraphQLSchemaTests` asserts both fields are in
   the running schema.
2. **`-pl <module>` does not install**, so `syson-application` was testing against a stale jar
   of the AI module. Worth remembering for every future cross-module check in this fork: install
   the module, or build with `-am`, before trusting an application-level test.
3. **The generate payload has to be a union.** `IExceptionWrapper` turns a failed generation into
   an `ErrorPayload`, so a plain object type would have failed at runtime on the error path only
   — the path least likely to be exercised by hand.

**Provenance carries no author yet, deliberately.** The input does not accept one: a
client-supplied author would let a caller stamp anyone's name on generated content, which is
worse than recording nothing because it looks authoritative. There is no server-side identity in
this codebase to read (nothing uses `SecurityContextHolder`), so the author is recorded as
`unknown` until that is wired. **This must be resolved before provenance here is treated as an
audit record.**

**Built next — the review UI.**

`SysONAiAssistantMenuEntry` (project menu) opens `SysONAiReviewDialog`, in
`frontend/syson/src/extensions/ai/`. The engineer selects elements, says what to derive, and gets
the candidates back as the exact textual SysML v2 that will be added — accepted or rejected one
at a time. Rejecting is purely local: it means "never apply this", so nothing about the model
changes.

- **Placement is constrained, not chosen.** `sirius-web-application` exposes no workbench
  side-panel extension point (the full list is navbar, footer, project menu, project settings,
  router, apollo and a few table/query hooks). A docked review panel would mean changing the
  platform, which CLAUDE.md rules out without a human decision, so the project menu it is.
- **The generate payload now returns the candidates**, not just a count, so review needs no second
  round trip.
- **Author now records origin, not identity** (`ai-assistant`), which is what the user asked for:
  enough to tell generated content from hand-authored content. Identity remains unwired.

**Read-back closes the loop.** `EditingContext.pendingAiProposals` returns proposals still
awaiting review, and the dialog resumes the most recent one when it opens. This is what makes
persisting a proposal worth anything: a generation that cost real money and real time survives a
refresh and can be picked up by someone other than whoever asked for it. Without it the store
would only ever have been written to.

Two decisions there. The fetcher reads the database **directly rather than through
`IEditingContextDispatcher`** — a proposal is not model data, it lives in its own tables, so
dispatching would queue a database query behind every pending edit on the project's
single-threaded executor for no benefit. And the field is named `pendingAiProposals` because that
is what it returns: applied and rejected proposals stay as history, and handing those to a review
UI would offer to apply work that has already been applied.

**Still to build:** refusal fallbacks on the Anthropic client.

**Sequencing.** The seven goals share one pipeline — scope selection → serialize context →
LLM call → parse and validate returned SysML → diff → apply with provenance — and differ
only in prompt, scoping strategy, and output shape. Build the pipeline once, then add thin
task adapters; this is not seven features. Start with **goal 3 (derived requirements)**: it
is the best-defined and already has the acceptance criterion. Goal 1 (ConOps) is tempting
because the chain starts there, but free narrative is the hardest output to validate, so
starting there means building the pipeline with the weakest test of whether it works.
Goal 7 is cheapest (item 3a already built the read/write services) but every generated link
inherits the non-standard `VerificationLink`, scaling the eventual migration cost.
Provenance has a known-good pattern: a `MetadataDefinition` in a model library
(`@Generated { by, prompt, at }`), same shape and loading trick as items 1-3.

**Enabling modules.** Two things make this far more tractable than it looks:

- `backend/application/syson-sysml-export` and `syson-sysml-import`, including
  `SysMLElementSerializer`, round-trip models to and from textual SysML v2.
- `backend/services/syson-sysml-rest-api-services` provides a clean read surface.

**Core architectural decision: never let the model manipulate EMF directly.**

Read path: serialize the relevant model scope to textual SysML v2 and send that as context.
Write path: either (a) the model returns textual SysML that gets reimported, so the parser
validates it and malformed output fails loudly instead of corrupting the model, or (b) for
narrow operations, expose a small set of typed tools that call the existing mutation
services so undo and validation keep working.

Prefer (b) for well-defined operations like "link these requirements to these components,"
and (a) for open-ended generation like deriving subsystem requirements.

**Components to build.**

1. `backend/services/syson-ai-services` — a new Maven module. LLM client, prompt assembly,
   model-scope serialization, response validation, and typed tool implementations that
   delegate to existing mutation services.
2. A GraphQL mutation on the backend exposing assistant operations.
3. A frontend extension panel registered from `frontend/syson/src/extensions/`. Follow the
   pattern in `SysONFooter.tsx` and `SysONNavigationBarIcon.tsx`.
4. A review/diff UI. Proposed changes must be presented for approval, never applied
   silently.

**Non-negotiables given the engineering context.**

- Human review is mandatory before any generated element enters the model.
- Record provenance on generated elements (what generated it, from what prompt, when).
  A derived requirement that silently entered a baseline is a problem you want to catch in
  the tool, not in a design review.
- Scope control: sending an entire large model as context will be slow and expensive.
  Design the scoping strategy (selected subtree, related elements by relationship traversal)
  early.
- API credentials must not be committed. Configure via Spring properties and environment
  variables.

**Acceptance:** a user can select a set of requirements and a subsystem, ask for candidate
derived requirements, review a diff, and accept or reject individual items, with accepted
items carrying provenance metadata.

---

## Cross-cutting

- **Rebase discipline.** Upstream commits daily and tags frequently (`v2026.7.0` through
  `v2026.7.4` in one cycle). Pin to a tag, rebase deliberately, keep our code in separate
  modules.
- **Testing.** Mirror the existing `CodingRulesTests` / `ArchitectureConstants` pattern in
  each new module. Integration tests exist for both Cypress and Playwright.
- **Interop as the invariant.** After each feature, verify that export to textual SysML v2
  and reimport is lossless. That is the check that catches accidental metamodel drift.
