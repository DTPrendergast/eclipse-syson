/*******************************************************************************
 * Copyright (c) 2025, 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.syson.table.requirements.view.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IObjectSearchService;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.components.representations.Message;
import org.eclipse.sirius.components.representations.MessageLevel;
import org.eclipse.sirius.components.tables.Table;
import org.eclipse.syson.requirements.library.services.RequirementIdSequenceQueryServices;
import org.eclipse.syson.services.DeleteService;
import org.eclipse.syson.sysml.Dependency;
import org.eclipse.syson.sysml.Documentation;
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.FeatureTyping;
import org.eclipse.syson.sysml.LiteralInteger;
import org.eclipse.syson.sysml.Membership;
import org.eclipse.syson.sysml.MembershipExpose;
import org.eclipse.syson.sysml.MetadataUsage;
import org.eclipse.syson.sysml.OwningMembership;
import org.eclipse.syson.sysml.RequirementUsage;
import org.eclipse.syson.sysml.SysmlFactory;
import org.eclipse.syson.sysml.Type;
import org.eclipse.syson.sysml.VerificationCaseUsage;
import org.eclipse.syson.sysml.ViewUsage;
import org.eclipse.syson.sysml.metamodel.services.ElementInitializerSwitch;
import org.eclipse.syson.sysml.services.api.ISysMLTextImporter;
import org.eclipse.syson.util.GetIntermediateContainerCreationSwitch;

/**
 * Mutation services for the Requirements Table View. The services declared in this class modify the model or the table
 * view.
 *
 * @author arichard
 */
public class RTVMutationServices {

    private static final String VERIFICATION_LINK_METADATA = "VerificationLink";

    private static final String THRESHOLD_OR_OBJECTIVE_METADATA = "ThresholdOrObjective";

    private static final String REQUIREMENT_CATEGORY_METADATA = "RequirementCategory";

    private static final String DEFAULT_REQUIREMENT_ID_PREFIX = "REQ-";

    private final IObjectSearchService objectSearchService;

    private final ISysMLTextImporter textImporter;

    private final DeleteService deleteService;

    private final RTVThresholdOrObjectiveMapping thresholdOrObjectiveMapping = new RTVThresholdOrObjectiveMapping();

    private final RTVRequirementTypeMapping requirementTypeMapping = new RTVRequirementTypeMapping();

    private final RequirementIdSequenceQueryServices requirementIdSequenceQueryServices = new RequirementIdSequenceQueryServices();

    public RTVMutationServices(IObjectSearchService objectSearchService, ISysMLTextImporter textImporter) {
        this.objectSearchService = Objects.requireNonNull(objectSearchService);
        this.textImporter = Objects.requireNonNull(textImporter);
        this.deleteService = new DeleteService();
    }

    public boolean removeFromExposedElements(RequirementUsage requirementUsage, IEditingContext editingContext, Table siriusTable) {
        var viewUsage = this.getViewUsage(editingContext, siriusTable);
        if (viewUsage != null) {
            if (viewUsage.getExposedElement().contains(requirementUsage)) {
                List<MembershipExpose> membershipsExposed = viewUsage.getOwnedRelationship().stream()
                        .filter(MembershipExpose.class::isInstance)
                        .map(MembershipExpose.class::cast)
                        .filter(membershipExposed -> Objects.equals(membershipExposed.getImportedElement(), requirementUsage))
                        .toList();
                var iterator = membershipsExposed.iterator();
                while (iterator.hasNext()) {
                    var membershipExpose = iterator.next();
                    this.deleteService.deleteFromModel(membershipExpose);
                }
                return true;
            }
        }
        return false;
    }

    private ViewUsage getViewUsage(IEditingContext editingContext, Table table) {
        var tableTargetObjectId = table.getTargetObjectId();
        return this.objectSearchService.getObject(editingContext, tableTargetObjectId).stream()
                .filter(ViewUsage.class::isInstance)
                .map(ViewUsage.class::cast)
                .findFirst()
                .orElse(null);
    }

    /**
     * Links the given requirement to the verification case declared with name {@code newValue} (matched
     * exactly, case-sensitively, against {@code VerificationCaseUsage.getDeclaredName()} across the whole
     * resource set), by applying the {@code VerificationLink} metadata workaround -- see that metadata's
     * Javadoc for why this exists instead of the standard "verify" relationship.
     * <p>
     * Clears the link (without creating a new one) if {@code newValue} is blank. Rejects the edit (returns
     * {@code false}, leaving any existing link untouched) if zero or more than one verification case matches --
     * a cell that silently links to the wrong case, or silently does nothing, is worse than an edit that visibly
     * fails.
     * </p>
     *
     * @param requirementUsage
     *            the requirement to link.
     * @param newValue
     *            the declared name of the verification case to link to, or blank to clear the link.
     * @param editingContext
     *            the editing context.
     * @return {@code true} if the edit was applied (including clearing the link), {@code false} if rejected.
     */
    public boolean setVerificationLink(RequirementUsage requirementUsage, String newValue, IEditingContext editingContext) {
        boolean success;
        if (newValue == null || newValue.isBlank()) {
            this.removeAppliedMetadata(requirementUsage, VERIFICATION_LINK_METADATA);
            success = true;
        } else {
            Optional<VerificationCaseUsage> match = this.findVerificationCaseByName(requirementUsage, newValue.strip());
            if (match.isEmpty()) {
                // No match, or more than one (findVerificationCaseByName only returns a value when exactly one
                // matches): reject rather than guess.
                success = false;
            } else {
                this.removeAppliedMetadata(requirementUsage, VERIFICATION_LINK_METADATA);
                String snippet = "@VerificationLink { verificationCase = %s; }".formatted(match.get().getQualifiedName());
                success = this.importSnippet(requirementUsage, snippet, editingContext);
            }
        }
        return success;
    }

    private Optional<VerificationCaseUsage> findVerificationCaseByName(RequirementUsage requirementUsage, String declaredName) {
        List<VerificationCaseUsage> matches = this.findAllInResourceSet(requirementUsage, VerificationCaseUsage.class).stream()
                .filter(candidate -> declaredName.equals(candidate.getDeclaredName()))
                .toList();
        return this.exactlyOne(matches);
    }

    /**
     * Links the given requirement to its parent requirements, identified by {@code newValue}, a comma-separated
     * list of parent reqIds (matched exactly against {@code RequirementUsage.getReqId()} across the whole
     * resource set). Represented as a {@code Dependency} owned by this requirement, with itself as
     * {@code client} and each resolved parent as {@code supplier} -- see
     * {@code RTVQueryServices.getParentRequirementIds}'s Javadoc for why this, and not a dedicated relationship
     * class.
     * <p>
     * Clears all parent links (without creating new ones) if {@code newValue} is blank. Rejects the edit
     * (returns {@code false}, leaving existing links untouched) if any identifier fails to resolve to exactly one
     * other requirement -- same silently-wrong-or-silently-nothing rationale as {@link #setVerificationLink}.
     * </p>
     */
    public boolean setParentRequirements(RequirementUsage requirementUsage, String newValue, IEditingContext editingContext) {
        boolean success;
        if (newValue == null || newValue.isBlank()) {
            this.removeParentRequirementLinks(requirementUsage);
            success = true;
        } else {
            List<String> identifiers = List.of(newValue.split(",")).stream()
                    .map(String::strip)
                    .filter(identifier -> !identifier.isBlank())
                    .distinct()
                    .toList();
            List<RequirementUsage> parents = new ArrayList<>();
            boolean allResolved = true;
            for (String identifier : identifiers) {
                Optional<RequirementUsage> match = this.findRequirementByReqId(requirementUsage, identifier);
                if (match.isEmpty()) {
                    allResolved = false;
                    break;
                }
                parents.add(match.get());
            }
            if (!allResolved || parents.isEmpty()) {
                success = false;
            } else {
                this.removeParentRequirementLinks(requirementUsage);
                String snippet = parents.stream()
                        .map(parent -> "dependency from %s to %s;".formatted(requirementUsage.getQualifiedName(), parent.getQualifiedName()))
                        .collect(Collectors.joining("\n"));
                success = this.importSnippet(requirementUsage, snippet, editingContext);
            }
        }
        return success;
    }

    private Optional<RequirementUsage> findRequirementByReqId(RequirementUsage requirementUsage, String reqId) {
        List<RequirementUsage> matches = this.findAllInResourceSet(requirementUsage, RequirementUsage.class).stream()
                .filter(candidate -> candidate != requirementUsage && reqId.equals(candidate.getReqId()))
                .toList();
        return this.exactlyOne(matches);
    }

    /**
     * Removes {@code Dependency} relationships owned by this requirement with itself as {@code client}. Handles
     * two shapes empirically confirmed for how a {@code dependency from ... to ...;} statement ends up owned:
     * directly in {@code ownedRelationship} (how {@code ISysMLTextImporter} places a single-relationship
     * snippet, the path this method's own caller uses) or wrapped in an {@code OwningMembership} (how a full
     * package parse places the same statement written inline in a requirement's body). Relying on only one shape
     * silently left the other one's links undeleted.
     */
    private void removeParentRequirementLinks(RequirementUsage requirementUsage) {
        List<Dependency> direct = requirementUsage.getOwnedRelationship().stream()
                .filter(Dependency.class::isInstance)
                .map(Dependency.class::cast)
                .toList();
        List<Dependency> wrapped = requirementUsage.getOwnedRelationship().stream()
                .filter(OwningMembership.class::isInstance)
                .map(OwningMembership.class::cast)
                .flatMap(owningMembership -> owningMembership.getOwnedRelatedElement().stream())
                .filter(Dependency.class::isInstance)
                .map(Dependency.class::cast)
                .toList();
        Stream.concat(direct.stream(), wrapped.stream())
                .filter(dependency -> dependency.getClient().contains(requirementUsage))
                .toList()
                .forEach(this.deleteService::deleteFromModel);
    }

    /**
     * Classifies the given requirement as one of the standard library's Requirement Types ({@code Functional},
     * {@code Interface}, {@code Performance}, {@code Physical}, {@code Design Constraint} -- see
     * {@link RTVRequirementTypeMapping}'s Javadoc), by adding a {@link FeatureTyping} to the corresponding
     * {@code Requirements::*RequirementCheck} definition alongside the requirement's own type(s).
     * <p>
     * Unlike the other cells in this class, this is not a textual reimport: this grammar has no standalone
     * syntax for adding a typing relationship outside of a usage's own declaration (checked
     * {@code SysMLv2Keywords} -- no {@code typing} keyword), so the {@link FeatureTyping} is built directly with
     * the EMF factory, mirroring the existing pattern in
     * {@code DetailsViewService.handleFeatureTypingNewValue}.
     * </p>
     * <p>
     * Clears the classification (without adding a new one) if {@code newValue} is blank. Rejects the edit if
     * {@code newValue} doesn't match one of the five known labels, or if that library type cannot be resolved
     * from this requirement's scope.
     * </p>
     */
    public boolean setRequirementType(RequirementUsage requirementUsage, String newValue) {
        this.removeRequirementTypeTyping(requirementUsage);
        boolean success;
        if (newValue == null || newValue.isBlank()) {
            success = true;
        } else {
            String qualifiedName = this.requirementTypeMapping.labelToQualifiedName(newValue);
            if (qualifiedName == null) {
                success = false;
            } else {
                Optional<Type> checkType = this.resolveLibraryType(requirementUsage, qualifiedName);
                if (checkType.isEmpty()) {
                    success = false;
                } else {
                    FeatureTyping featureTyping = SysmlFactory.eINSTANCE.createFeatureTyping();
                    featureTyping.setTypedFeature(requirementUsage);
                    featureTyping.setType(checkType.get());
                    requirementUsage.getOwnedRelationship().add(featureTyping);
                    success = true;
                }
            }
        }
        return success;
    }

    private Optional<Type> resolveLibraryType(RequirementUsage requirementUsage, String qualifiedName) {
        Membership membership = requirementUsage.resolve(qualifiedName);
        Optional<Type> result = Optional.empty();
        if (membership != null && membership.getMemberElement() instanceof Type type) {
            result = Optional.of(type);
        }
        return result;
    }

    private void removeRequirementTypeTyping(RequirementUsage requirementUsage) {
        requirementUsage.getOwnedRelationship().stream()
                .filter(FeatureTyping.class::isInstance)
                .map(FeatureTyping.class::cast)
                .filter(featureTyping -> featureTyping.getType() != null
                        && this.requirementTypeMapping.entries().containsValue(featureTyping.getType().getQualifiedName()))
                .toList()
                .forEach(this.deleteService::deleteFromModel);
    }

    /**
     * Sets or clears the {@code ThresholdOrObjective} metadata on the given requirement. Accepts either the
     * display label ({@code Threshold}/{@code Objective}) or the underlying enum literal, case-insensitively;
     * rejects anything else.
     */
    public boolean setThresholdOrObjective(RequirementUsage requirementUsage, String newValue, IEditingContext editingContext) {
        this.removeAppliedMetadata(requirementUsage, THRESHOLD_OR_OBJECTIVE_METADATA);
        boolean success;
        if (newValue == null || newValue.isBlank()) {
            success = true;
        } else {
            String literal = this.thresholdOrObjectiveMapping.labelOrLiteralToLiteral(newValue);
            if (literal == null) {
                success = false;
            } else {
                String reference = this.thresholdOrObjectiveMapping.literalToSnippetReference(literal);
                // Fully qualified, not just the metadata name: unlike VerificationMethodKind (visible unqualified
                // wherever VerificationMethod itself resolves), ThresholdOrObjectiveKind is not implicitly
                // visible here -- an unqualified reference fails to resolve with only a WARNING (not an ERROR),
                // so importSnippet's hasErrors check alone would not have caught it. Confirmed via a scratch test.
                String snippet = "@RequirementAttributesLibrary::ThresholdOrObjective { kind = RequirementAttributesLibrary::ThresholdOrObjectiveKind::%s; }".formatted(reference);
                success = this.importSnippet(requirementUsage, snippet, editingContext);
            }
        }
        return success;
    }

    /**
     * Sets or clears the free-text {@code RequirementCategory} metadata on the given requirement. Rejects (rather
     * than attempting to escape) any value containing a {@code "} character: confirmed via a scratch test that
     * this grammar's string literals have no working escape for an embedded quote at all -- neither
     * backslash-escaping (silently truncates the string there, reporting a misleading WARNING about the
     * remainder instead of an error) nor doubling it, the SQL/Pascal-style convention (also silently truncates,
     * with no message at all). A visibly-rejected edit beats one that silently corrupts the value. Backslashes
     * need no escaping and round-trip correctly as-is.
     */
    public boolean setCategory(RequirementUsage requirementUsage, String newValue, IEditingContext editingContext) {
        boolean success;
        if (newValue != null && newValue.contains("\"")) {
            success = false;
        } else {
            this.removeAppliedMetadata(requirementUsage, REQUIREMENT_CATEGORY_METADATA);
            if (newValue == null || newValue.isBlank()) {
                success = true;
            } else {
                String snippet = "@RequirementAttributesLibrary::RequirementCategory { category = \"%s\"; }".formatted(newValue);
                success = this.importSnippet(requirementUsage, snippet, editingContext);
            }
        }
        return success;
    }

    private void removeAppliedMetadata(Element element, String metadataDefinitionName) {
        element.getOwnedRelationship().stream()
                .filter(OwningMembership.class::isInstance)
                .map(OwningMembership.class::cast)
                .flatMap(owningMembership -> owningMembership.getOwnedRelatedElement().stream())
                .filter(MetadataUsage.class::isInstance)
                .map(MetadataUsage.class::cast)
                .filter(metadataUsage -> metadataUsage.getMetadataDefinition() != null
                        && metadataDefinitionName.equals(metadataUsage.getMetadataDefinition().getName()))
                .toList()
                .forEach(this.deleteService::deleteFromModel);
    }

    private <T> List<T> findAllInResourceSet(Element anchor, Class<T> type) {
        List<T> results = new ArrayList<>();
        Resource resource = anchor.eResource();
        if (resource != null && resource.getResourceSet() != null) {
            for (Resource candidateResource : resource.getResourceSet().getResources()) {
                var iterator = candidateResource.getAllContents();
                while (iterator.hasNext()) {
                    var next = iterator.next();
                    if (type.isInstance(next)) {
                        results.add(type.cast(next));
                    }
                }
            }
        }
        return results;
    }

    private <T> Optional<T> exactlyOne(List<T> matches) {
        Optional<T> result = Optional.empty();
        if (matches.size() == 1) {
            result = Optional.of(matches.get(0));
        }
        return result;
    }

    private boolean importSnippet(Element parentElement, String snippet, IEditingContext editingContext) {
        boolean success = false;
        if (editingContext instanceof IEMFEditingContext emfEditingContext) {
            List<Message> messages = new ArrayList<>();
            var createdElements = this.textImporter.importSysMLText(emfEditingContext, parentElement, snippet, messages);
            boolean hasErrors = messages.stream().anyMatch(message -> message.level() == MessageLevel.ERROR);
            success = !createdElements.isEmpty() && !hasErrors;
        }
        return success;
    }

    public boolean editDocumentation(RequirementUsage requirementUsage, String newValue) {
        Documentation documentation = requirementUsage.getDocumentation().stream()
                .findFirst()
                .orElse(null);
        if (documentation != null) {
            documentation.setBody(newValue);
        } else {
            var newDocumentation = SysmlFactory.eINSTANCE.createDocumentation();
            newDocumentation.setBody(newValue);
            var owningMembership = SysmlFactory.eINSTANCE.createOwningMembership();
            requirementUsage.getOwnedRelationship().add(owningMembership);
            owningMembership.getOwnedRelatedElement().add(newDocumentation);
        }
        return true;
    }

    /**
     * Creates a new RequirementUsage visible inside the given ViewUsage.
     * The owning element of the given ViewUsage is the parent of the new created RequirementUsage.
     *
     * @param viewUsage
     *         the ViewUsage containing the table in which the new RequirementUsage will be created.
     * @param editingContext
     *         the (non-{@code null}) {@link IEditingContext}, used to persist the requirement-ID sequence's
     *         incremented counter.
     * @return <code>true</code> if creation successes and <code>false</code> otherwise.
     */
    public boolean createRequirement(ViewUsage viewUsage, IEditingContext editingContext) {
        var owningNamespace = viewUsage.getOwningNamespace();
        return this.createRequirement(owningNamespace, viewUsage, editingContext) != null;
    }

    private RequirementUsage createRequirement(Element parent, ViewUsage viewUsage, IEditingContext editingContext) {
        RequirementUsage newRequirementUsage = SysmlFactory.eINSTANCE.createRequirementUsage();
        Optional<EClass> optMembershipEClass = new GetIntermediateContainerCreationSwitch(parent).doSwitch(newRequirementUsage.eClass());
        if (optMembershipEClass.isPresent()) {
            var newMembership = SysmlFactory.eINSTANCE.create(optMembershipEClass.get());
            if (newMembership instanceof Membership membership) {
                var elementInitializerSwitch = new ElementInitializerSwitch();
                parent.getOwnedRelationship().add(membership);
                membership.getOwnedRelatedElement().add(newRequirementUsage);
                elementInitializerSwitch.doSwitch(newRequirementUsage);
                // Auto-assigned reqId: one sequence per table (scoped to the view's owning namespace, the same
                // for both root and nested requirements), not per parent -- see RequirementIdSequence's Javadoc.
                // The existing ReqId cell (RTVTableDescriptionProvider's plain SetValue on the "reqId" feature)
                // still works unchanged for a user who wants to override this.
                newRequirementUsage.setReqId(this.assignNextRequirementId(viewUsage.getOwningNamespace(), editingContext));
                var membershipExpose = SysmlFactory.eINSTANCE.createMembershipExpose();
                viewUsage.getOwnedRelationship().add(membershipExpose);
                elementInitializerSwitch.doSwitch(membership);
                membershipExpose.setImportedMembership(membership);
                return newRequirementUsage;
            }
        }
        return null;
    }

    /**
     * Returns the next auto-assigned requirement ID (e.g. {@code "SYS-001"}), reading and incrementing the
     * {@code RequirementIdSequence} metadata applied on {@code scope}. If no such metadata is applied yet,
     * creates one with the default prefix ({@value #DEFAULT_REQUIREMENT_ID_PREFIX}) starting at 1.
     * <p>
     * Incrementing mutates the existing {@code nextNumber} {@link LiteralInteger} in place rather than
     * reimporting the whole metadata -- avoids re-deriving its redefinition structure for what is otherwise a
     * single {@code EAttribute} flip. Creating a missing sequence, on the other hand, goes through the usual
     * textual reimport, consistent with every other metadata application in this class.
     * </p>
     */
    private String assignNextRequirementId(Element scope, IEditingContext editingContext) {
        Optional<LiteralInteger> nextNumberLiteral = this.requirementIdSequenceQueryServices.getNextNumberLiteral(scope);
        String reqId;
        if (nextNumberLiteral.isPresent()) {
            String prefix = this.requirementIdSequenceQueryServices.getPrefix(scope).orElse(DEFAULT_REQUIREMENT_ID_PREFIX);
            int number = nextNumberLiteral.get().getValue();
            nextNumberLiteral.get().setValue(number + 1);
            reqId = "%s%03d".formatted(prefix, number);
        } else {
            reqId = "%s%03d".formatted(DEFAULT_REQUIREMENT_ID_PREFIX, 1);
            String snippet = "@RequirementAttributesLibrary::RequirementIdSequence { prefix = \"%s\"; nextNumber = 2; }".formatted(DEFAULT_REQUIREMENT_ID_PREFIX);
            this.importSnippet(scope, snippet, editingContext);
        }
        return reqId;
    }

    /**
     * Creates a nested requirement (inside a given RequirementUsage).
     *
     * @param parent
     *         the RequirementUsage owing the new created RequirementUsage.
     * @param editingContext
     *         the (non-{@code null}) {@link IEditingContext}.
     * @param table
     *         the table in which the Requirement will be created.
     * @return the newly created RequirementUsage.
     */
    public RequirementUsage createNestedRequirement(RequirementUsage parent, IEditingContext editingContext, Table table) {
        var viewUsage = this.getViewUsage(editingContext, table);
        if (viewUsage != null) {
            return this.createRequirement(parent, viewUsage, editingContext);
        }
        return null;
    }
}
