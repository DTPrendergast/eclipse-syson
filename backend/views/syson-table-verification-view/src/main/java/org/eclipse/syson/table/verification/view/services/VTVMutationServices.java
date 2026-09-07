/*******************************************************************************
 * Copyright (c) 2026 Obeo.
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
package org.eclipse.syson.table.verification.view.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IObjectSearchService;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.components.representations.Message;
import org.eclipse.sirius.components.representations.MessageLevel;
import org.eclipse.sirius.components.tables.Table;
import org.eclipse.syson.services.DeleteService;
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.Membership;
import org.eclipse.syson.sysml.MembershipExpose;
import org.eclipse.syson.sysml.MetadataUsage;
import org.eclipse.syson.sysml.OwningMembership;
import org.eclipse.syson.sysml.SysmlFactory;
import org.eclipse.syson.sysml.VerificationCaseUsage;
import org.eclipse.syson.sysml.ViewUsage;
import org.eclipse.syson.sysml.metamodel.services.ElementInitializerSwitch;
import org.eclipse.syson.sysml.services.api.ISysMLTextImporter;
import org.eclipse.syson.util.GetIntermediateContainerCreationSwitch;

/**
 * Mutation services for the Verification Table View. The services declared in this class modify the model or the
 * table view.
 *
 * <p>
 * Setting the verification method or procedure ID re-imports a small textual SysML fragment (e.g.
 * {@code @VerificationMethod { kind = VerificationMethodKind::test; }}) via {@link ISysMLTextImporter} rather than
 * hand-building the underlying {@code MetadataUsage}/{@code ReferenceUsage}/{@code FeatureValue} structure with the
 * EMF factory. This follows the same "reimport validates it" principle as plan.md item 4, at cell-edit scale: it
 * reuses the same parser every other textual edit in the application goes through, instead of a second,
 * hand-rolled path that risks silently diverging from what the parser itself would produce.
 * </p>
 *
 * @author DTPrendergast
 */
public class VTVMutationServices {

    private final IObjectSearchService objectSearchService;

    private final ISysMLTextImporter textImporter;

    private final DeleteService deleteService;

    private final VTVKindMapping kindMapping = new VTVKindMapping();

    public VTVMutationServices(IObjectSearchService objectSearchService, ISysMLTextImporter textImporter) {
        this.objectSearchService = Objects.requireNonNull(objectSearchService);
        this.textImporter = Objects.requireNonNull(textImporter);
        this.deleteService = new DeleteService();
    }

    public boolean setVerificationMethod(VerificationCaseUsage verificationCaseUsage, String newValue, IEditingContext editingContext) {
        this.removeAppliedMetadata(verificationCaseUsage, "VerificationMethod");
        boolean success;
        if (newValue == null || newValue.isBlank()) {
            success = true;
        } else {
            String literal = this.kindMapping.labelOrLiteralToLiteral(newValue);
            if (literal == null) {
                // Not one of Test/Demonstration/Analysis/Inspection: reject the edit rather than silently accepting
                // a value that would not resolve, or worse, fail to reimport later.
                success = false;
            } else {
                String snippet = "@VerificationMethod { kind = VerificationMethodKind::%s; }".formatted(literal);
                success = this.importSnippet(verificationCaseUsage, snippet, editingContext);
            }
        }
        return success;
    }

    /**
     * Rejects (rather than attempting to escape) any value containing a {@code "} character: confirmed via a
     * scratch test (see {@code syson-table-requirements-view}'s {@code RTVMutationServices.setCategory}, which
     * had the same bug) that this grammar's string literals have no working escape for an embedded quote at all
     * -- neither backslash-escaping nor doubling it silently corrupts the value instead of erroring, so the
     * previous escaping here would have silently truncated any procedure ID containing a quote. Backslashes need
     * no escaping and round-trip correctly as-is.
     */
    public boolean setProcedureId(VerificationCaseUsage verificationCaseUsage, String newValue, IEditingContext editingContext) {
        boolean success;
        if (newValue != null && newValue.contains("\"")) {
            success = false;
        } else {
            this.removeAppliedMetadata(verificationCaseUsage, "VerificationProcedure");
            if (newValue == null || newValue.isBlank()) {
                success = true;
            } else {
                String snippet = "@VerificationProcedureLibrary::VerificationProcedure { procedureId = \"%s\"; }".formatted(newValue);
                success = this.importSnippet(verificationCaseUsage, snippet, editingContext);
            }
        }
        return success;
    }

    public boolean removeFromExposedElements(VerificationCaseUsage verificationCaseUsage, IEditingContext editingContext, Table siriusTable) {
        var viewUsage = this.getViewUsage(editingContext, siriusTable);
        if (viewUsage != null && viewUsage.getExposedElement().contains(verificationCaseUsage)) {
            viewUsage.getOwnedRelationship().stream()
                    .filter(MembershipExpose.class::isInstance)
                    .map(MembershipExpose.class::cast)
                    .filter(membershipExpose -> Objects.equals(membershipExpose.getImportedElement(), verificationCaseUsage))
                    .toList()
                    .forEach(this.deleteService::deleteFromModel);
            return true;
        }
        return false;
    }

    /**
     * Creates a new {@code VerificationCaseUsage} visible inside the given {@code ViewUsage}. The owning element of
     * the given {@code ViewUsage} is the parent of the new element.
     *
     * @param viewUsage
     *            the {@code ViewUsage} containing the table in which the new {@code VerificationCaseUsage} will be
     *            created.
     * @return {@code true} if creation succeeds, {@code false} otherwise.
     */
    public boolean createVerificationCase(ViewUsage viewUsage) {
        var owningNamespace = viewUsage.getOwningNamespace();
        VerificationCaseUsage newVerificationCaseUsage = SysmlFactory.eINSTANCE.createVerificationCaseUsage();
        Optional<EClass> optMembershipEClass = new GetIntermediateContainerCreationSwitch(owningNamespace).doSwitch(newVerificationCaseUsage.eClass());
        if (optMembershipEClass.isPresent() && SysmlFactory.eINSTANCE.create(optMembershipEClass.get()) instanceof Membership membership) {
            var elementInitializerSwitch = new ElementInitializerSwitch();
            owningNamespace.getOwnedRelationship().add(membership);
            membership.getOwnedRelatedElement().add(newVerificationCaseUsage);
            elementInitializerSwitch.doSwitch(newVerificationCaseUsage);
            var membershipExpose = SysmlFactory.eINSTANCE.createMembershipExpose();
            viewUsage.getOwnedRelationship().add(membershipExpose);
            elementInitializerSwitch.doSwitch(membership);
            membershipExpose.setImportedMembership(membership);
            return true;
        }
        return false;
    }

    private boolean importSnippet(Element parentElement, String snippet, IEditingContext editingContext) {
        if (editingContext instanceof IEMFEditingContext emfEditingContext) {
            List<Message> messages = new ArrayList<>();
            var createdElements = this.textImporter.importSysMLText(emfEditingContext, parentElement, snippet, messages);
            boolean hasErrors = messages.stream().anyMatch(message -> message.level() == MessageLevel.ERROR);
            return !createdElements.isEmpty() && !hasErrors;
        }
        return false;
    }

    /**
     * Removes any {@code MetadataUsage} directly owned by {@code element} whose metadata definition is named
     * {@code metadataDefinitionName}, if present. Used before re-importing a fresh one, so editing a cell replaces
     * rather than accumulates applied metadata.
     */
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

    private ViewUsage getViewUsage(IEditingContext editingContext, Table table) {
        var tableTargetObjectId = table.getTargetObjectId();
        return this.objectSearchService.getObject(editingContext, tableTargetObjectId).stream()
                .filter(ViewUsage.class::isInstance)
                .map(ViewUsage.class::cast)
                .findFirst()
                .orElse(null);
    }
}
