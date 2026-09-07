standard library package RequirementAttributesLibrary {
    doc /*
     * SysON project addition to the SysML v2 standard library.
     *
     * Adds two requirement-table concepts that have no standard SysML v2 equivalent (checked:
     * neither "Threshold or Objective" nor a free-text "Category" appear anywhere in the 90
     * files that make up the bundled standard library). "Requirement Type" (Functional,
     * Interface, Performance, Physical, Design Constraint), by contrast, does not need
     * anything from this library: it is modeled by specializing the standard library's own
     * Requirements::FunctionalRequirementCheck / InterfaceRequirementCheck /
     * PerformanceRequirementCheck / PhysicalRequirementCheck / DesignConstraintCheck
     * RequirementDefinitions -- see RTVRequirementTypeMapping's Javadoc in
     * syson-table-requirements-view.
     *
     * This file is the canonical, human-authored source of truth. It is not loaded
     * directly at runtime: it is compiled into ../sysml.libraries/RequirementAttributes.json
     * by RequirementAttributesLibraryGenerator (see src/test/java), which is re-run by hand
     * whenever this file changes. See this module's README.md for details.
     */

    private import ScalarValues::String;
    private import ScalarValues::Integer;

    enum def ThresholdOrObjectiveKind {
        doc /*
         * Whether a quantitative requirement value is the minimally-acceptable bound
         * (threshold) or the desired, better-than-minimum target (objective) -- the standard
         * measure-of-effectiveness/KPP framing used across systems engineering requirements
         * tables, with no equivalent already in the SysML v2 standard library.
         */
        // "objective" is escaped with single quotes: it is a reserved keyword in this grammar (see
        // SysMLv2Keywords.OBJECTIVE, used for Case objective requirements), and using it unescaped as a plain
        // identifier here silently truncates the rest of the file's parse with no reported error -- confirmed
        // via a scratch test dumping the raw parse tree.
        enum threshold;
        enum 'objective';
    }

    metadata def ThresholdOrObjective {
        doc /*
         * Classifies a RequirementUsage as expressing a threshold or an objective value.
         */
        attribute kind : ThresholdOrObjectiveKind;

        :> annotatedElement : SysML::RequirementUsage;
    }

    metadata def RequirementCategory {
        doc /*
         * A free-text category label for a requirement (e.g. "Safety", "Reliability"). This
         * library does not assume or restrict any particular category taxonomy.
         */
        attribute category : String;

        :> annotatedElement : SysML::RequirementUsage;
    }

    metadata def RequirementIdSequence {
        doc /*
         * Carries the auto-assignment state for the requirements table's "Create Requirement"
         * tool: a user-settable ID prefix (e.g. "SYS-") and the next number to assign (e.g.
         * 12, to produce "SYS-012"). Applied on the requirements table's own owning namespace
         * (typically a Package), not on individual requirements -- one sequence per table.
         *
         * nextNumber is a monotonically-increasing counter, not derived from scanning existing
         * reqIds: deleting the highest-numbered requirement must not free its number for reuse.
         * RTVMutationServices increments it by mutating the LiteralInteger value in place after
         * assigning nextNumber's current value, rather than reimporting the whole metadata.
         *
         * Unrestricted (no ":> annotatedElement" restriction), same reasoning as
         * OperationalConcept in syson-operational-concept-library: Package/Namespace are not
         * reflectively exposed as metaclasses anywhere in the bundled standard library, so
         * there is no SysML::Package to restrict against.
         *
         * To use a different prefix than the default ("REQ-"), apply this metadata by hand
         * (e.g. "@RequirementAttributesLibrary::RequirementIdSequence { prefix = "SYS-";
         * nextNumber = 1; }") on the table's owning package before creating the first
         * requirement through the table -- this project does not (yet) provide a dedicated
         * settings UI for it, matching this library's minimal-new-UI stance elsewhere.
         */
        attribute prefix : String;
        attribute nextNumber : Integer;
    }
}
