standard library package OperationalConceptLibrary {
    doc /*
     * SysON project addition to the SysML v2 standard library.
     *
     * An operational concept is a package containing use cases, stakeholder/actor parts,
     * and a documentation body describing users and how they intend to use the system. All
     * of that is already ordinary SysML v2 (UseCaseUsage, PartUsage, Documentation) -- this
     * metadata definition only makes such a package identifiable and distinguishable (e.g.
     * a dedicated icon in the explorer), so it does not introduce any new modeling concept
     * of its own. It carries no attributes.
     *
     * Unlike VerificationProcedure (restricted to VerificationCaseUsage via
     * "annotatedElement"), this is unrestricted: unlike usage/definition kinds, Package and
     * Namespace are not reflectively exposed as metaclasses anywhere in the standard
     * library (checked every sysml.libraries/*.json and kerml.libraries/*.json file), so
     * there is no "SysML::Package" to restrict against. Nothing stops this from being
     * applied to a non-package element; that is a soft convention, not an enforced one.
     *
     * This file is the canonical, human-authored source of truth. It is not loaded directly
     * at runtime: it is compiled into ../sysml.libraries/OperationalConcept.json by
     * OperationalConceptLibraryGenerator (see src/test/java), which is re-run by hand
     * whenever this file changes. See this module's README.md for details.
     */

    metadata def OperationalConcept;
}
