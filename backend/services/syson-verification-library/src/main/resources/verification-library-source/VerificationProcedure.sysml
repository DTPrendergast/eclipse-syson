standard library package VerificationProcedureLibrary {
    doc /*
     * SysON project addition to the SysML v2 standard library.
     *
     * The standard library (sysml.libraries/VerificationCases.json) already defines
     * VerificationMethodKind (test/demo/analyze/inspect) and the VerificationMethod
     * metadata definition that carries it. This library adds only what is missing:
     * a procedure identifier, meant to be applied on a VerificationCaseUsage alongside
     * the standard VerificationMethod metadata, not instead of it.
     *
     * This file is the canonical, human-authored source of truth. It is not loaded
     * directly at runtime: it is compiled into ../sysml.libraries/VerificationProcedure.json
     * by VerificationProcedureLibraryGenerator (see src/test/java), which is re-run by
     * hand whenever this file changes. See this module's README.md for details.
     */

    private import ScalarValues::String;

    metadata def VerificationProcedure {
        doc /*
         * Identifies the procedure document/section that a verification case executes,
         * e.g. "SYS-TP-014" or "Section 4.2.1". Free text: this library does not assume
         * any particular procedure numbering or document management scheme.
         */
        attribute procedureId : String;

        :> annotatedElement : SysML::VerificationCaseUsage;
    }

    metadata def VerificationLink {
        doc /*
         * Stand-in for the standard "verify" relationship (RequirementVerificationMembership),
         * applied on the RequirementUsage being verified rather than on the case, pointing at
         * the VerificationCaseUsage that verifies it.
         *
         * This exists only because, as of the generated metamodel this project builds against,
         * VerificationCaseUsage.getVerifiedRequirement() unconditionally returns an empty list
         * and RequirementVerificationMembershipImpl.basicGetOwnedRequirement()/
         * basicGetVerifiedRequirement() are literal unimplemented stubs (`// TODO: implement
         * this method`, returning null) -- confirmed by reading
         * backend/metamodel/.../impl/VerificationCaseUsageImpl.java and
         * .../RequirementVerificationMembershipImpl.java directly. The standard relationship
         * cannot be read back (and, empirically, "verify req1;" does not even get authored by
         * the bundled syside-cli), so the requirements table's verification-procedure-ID column
         * (docs/plan.md, task 3a) uses this instead. It is a workaround, not a first-class
         * SysML v2 concept: a model exported with this metadata will not be recognized as
         * having a "verify" relationship by another SysML v2 tool. Retire this in favor of the
         * standard relationship once the upstream metamodel bug is fixed.
         */
        ref verificationCase : VerificationCases::VerificationCase;

        :> annotatedElement : SysML::RequirementUsage;
    }
}
