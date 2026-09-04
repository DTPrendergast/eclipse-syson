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

**What already exists.** Most of this is standard SysML v2 and is already in the metamodel:

| Concept                       | Metamodel class                                          |
| ----------------------------- | -------------------------------------------------------- |
| Verification case             | `VerificationCaseDefinition`, `VerificationCaseUsage`    |
| Analysis case                 | `AnalysisCaseDefinition`, `AnalysisCaseUsage`            |
| Requirement→verification link | `RequirementVerificationMembership`                      |
| Requirement→component link    | `SatisfyRequirementUsage`                                |
| User-defined semantics        | `MetadataDefinition`, `MetadataUsage`, `MetadataFeature` |

`RequirementVerificationMembership` is the `verify` relationship. It is the link we want;
do not invent a parallel one.

**What to build.**

1. **A SysML model library** (`.sysml`) defining our project-specific additions:
   - An enumeration of verification methods (Test, Demonstration, Analysis, Inspection).
   - A `MetadataDefinition` carrying `verificationMethod` and `procedureId`, applicable to
     verification cases.
   - Optionally, named `VerificationCaseDefinition`s to type against.
2. **Library registration** so it loads as a standard library alongside the SysML ones.
   Find how upstream registers the standard libraries and follow that path.
3. **Palette and tree entries** so users can create verification cases without hand-typing.
4. **A verification table view** (see item 2 — same pattern as the requirements table,
   new Maven module `backend/views/syson-table-verification-view`).

**Explicitly out of scope:** adding classes to `backend/metamodel`.

**Acceptance:** a user can create a verification case, set its method and procedure ID,
link it to a requirement, export the model to textual SysML v2, and reimport it without loss.

---

## 2. Operational concept objects

**Goal:** A mostly text-based object describing users and how they intend to use the system.

**What already exists.** `UseCaseDefinition`, `UseCaseUsage`, `IncludeUseCaseUsage`,
`ConcernDefinition`, `ConcernUsage`, `FramedConcernMembership`. Documentation bodies are
already supported and already surfaced (the requirements table reads them via
`getDocumentationBody()`). The frontend already depends on
`@eclipse-sirius/sirius-components-markdown`.

**What to build.**

1. **A model pattern:** an operational concept is a package containing use cases,
   stakeholder/actor parts, and a documentation body, marked with a `MetadataDefinition`
   from our library so it is identifiable, filterable, and styleable.
2. **A form or markdown-based representation** for authoring the narrative text, rather
   than forcing users through the tree. Check what
   `@eclipse-sirius/sirius-components-markdown` and the form widgets give us before
   writing anything custom.
3. **Explorer integration:** icon, filter, and a creation tool.

**Open question to settle first:** should the operational concept be a first-class
representation with its own editor, or a documented package that existing views already
handle? Prototype the second; it may be enough.

**Acceptance:** a user can author an operational concept narrative, reference use cases
from it, and see it distinguished in the explorer.

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

**Task 3a — verification procedure ID column (small).**
Add a `ColumnDescription` plus a `CellDescription` whose `valueExpression` walks the
requirement's `RequirementVerificationMembership` to the linked verification case and reads
the `procedureId` metadata from item 1. Roughly 20 lines plus a query service method in
`RTVQueryServices`. Register any new service class in `RTVJavaServiceProvider` or the AQL
will not resolve.

Making it editable means a cell edit handler in `RTVMutationServices` that creates or
retargets the `RequirementVerificationMembership`. Consider whether a reference-picker cell
is better than free text here — a text cell that silently fails to match an existing
verification case is a trap.

**Task 3b — four fixed columns (medium).**
Same `ColumnDescription`/`CellDescription` pattern as 3a, added to the same
`RTVTableDescriptionProvider`, one query-service method and one edit handler each in
`RTVQueryServices`/`RTVMutationServices`:

- **Parent Requirements** — multi-valued reference column. Entering a requirement ID
  creates a link to that parent requirement in the model; a row may have more than one.
  Checked the metamodel: unlike verify/satisfy, there is no dedicated requirement-to-
  requirement relationship class (no `DeriveRequirementUsage`/`RequirementDerivation`, and
  no `Dependency` subclasses at all exist in `backend/metamodel`). The mechanism to use is
  the generic `Dependency` (`client`/`supplier`), with this requirement as `client` and the
  entered parent as `supplier` — SysML v2's actual, untyped trace/derive construct here, not
  a parallel relationship we'd be inventing. Almost certainly wants a reference-picker cell
  rather than free text, for the same silently-fails-to-match reason as the procedure-ID
  column in 3a.
- **Threshold or Objective** — dropdown cell, two values. Requires a dropdown-style
  `CellDescription` widget rather than a text one; confirm Sirius Web's table component
  supports one before committing to this shape. Decide whether the two values are a plain
  Java enum/constant list or an enumeration defined in item 1's SysML model library —
  prefer the library if item 1 already exists, for consistency.
- **Requirement Type** — dropdown cell, fixed values `Functional`, `Performance`,
  `Non-functional`. Same dropdown mechanism and same enum-location decision as Threshold
  or Objective; keep both consistent with each other.
- **Category** — free-text column, same shape as the existing `Documentation` column.
  Smallest of the four; no new UI widget needed.

**Task 3c — automatic requirement ID assignment (small–medium).**
`RTVTableCreateRequirementToolExecutor` → `RTVMutationServices.createRequirement` should
assign `reqId` automatically instead of leaving it blank for the user to type in the 3a
column. Needs:

- A user-settable prefix (e.g. `SYS-` → `SYS-001`). Decide where the prefix is stored —
  candidates are a property on the containing package, or a `MetadataDefinition` from item
  1's library; either should be exportable and versioned along with the rest of the model.
- A next-available-number scheme scoped to wherever the prefix lives, that doesn't collide
  or reuse numbers when requirements are deleted or created out of order.
- The existing 3a edit handler on `reqId` still needs to work, for the case where a user
  wants to override the auto-assigned value.

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

---

## 4. AI modeling assistant

**Goal:** Ask an agent to recommend changes, link requirements to components, and derive
subsystem requirements from the architecture.

**Current state.** Nothing. No LLM integration anywhere in the tree.

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
