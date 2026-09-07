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
package org.eclipse.syson.verification.library.services;

import java.util.Optional;

import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.FeatureReferenceExpression;
import org.eclipse.syson.sysml.MetadataUsage;
import org.eclipse.syson.sysml.OwningMembership;
import org.eclipse.syson.sysml.ReferenceUsage;
import org.eclipse.syson.sysml.RequirementUsage;
import org.eclipse.syson.sysml.VerificationCaseUsage;
import org.eclipse.syson.sysml.metamodel.services.MetamodelQueryElementService;

/**
 * Reads the {@code VerificationLink} metadata (see {@code VerificationProcedure.sysml}'s Javadoc for why this
 * exists: a workaround for {@code VerificationCaseUsage.getVerifiedRequirement()} being an unconditional
 * empty-list stub in the generated metamodel, not a first-class SysML v2 concept).
 *
 * @author DTPrendergast
 */
public class VerificationLinkQueryServices {

    private static final String VERIFICATION_LINK_METADATA = "VerificationLink";

    private static final String VERIFICATION_CASE_FEATURE = "verificationCase";

    // Not constructor-injected: see VTVQueryServices' comment on why MetamodelQueryElementService (a plain
    // utility class, not a Spring bean) must not be a constructor parameter of a class Sirius Web instantiates
    // via AutowireCapableBeanFactory.createBean(Class).
    private final MetamodelQueryElementService metamodelQueryElementService = new MetamodelQueryElementService();

    /**
     * Returns the {@code VerificationCaseUsage} linked to the given requirement via {@code VerificationLink}
     * metadata, if any.
     *
     * @param requirementUsage
     *            a {@code RequirementUsage}.
     * @return the linked verification case, or {@link Optional#empty()}.
     */
    public Optional<VerificationCaseUsage> getLinkedVerificationCase(RequirementUsage requirementUsage) {
        return this.getAppliedMetadata(requirementUsage, VERIFICATION_LINK_METADATA)
                .flatMap(metadataUsage -> this.getFeature(metadataUsage, VERIFICATION_CASE_FEATURE))
                .flatMap(this.metamodelQueryElementService::getValueExpression)
                .filter(FeatureReferenceExpression.class::isInstance)
                .map(FeatureReferenceExpression.class::cast)
                .map(FeatureReferenceExpression::getReferent)
                .filter(VerificationCaseUsage.class::isInstance)
                .map(VerificationCaseUsage.class::cast);
    }

    private Optional<MetadataUsage> getAppliedMetadata(Element element, String metadataDefinitionName) {
        return element.getOwnedRelationship().stream()
                .filter(OwningMembership.class::isInstance)
                .map(OwningMembership.class::cast)
                .flatMap(owningMembership -> owningMembership.getOwnedRelatedElement().stream())
                .filter(MetadataUsage.class::isInstance)
                .map(MetadataUsage.class::cast)
                .filter(metadataUsage -> metadataUsage.getMetadataDefinition() != null
                        && metadataDefinitionName.equals(metadataUsage.getMetadataDefinition().getName()))
                .findFirst();
    }

    private Optional<ReferenceUsage> getFeature(MetadataUsage metadataUsage, String featureName) {
        return metadataUsage.getOwnedRelationship().stream()
                .filter(OwningMembership.class::isInstance)
                .map(OwningMembership.class::cast)
                .flatMap(owningMembership -> owningMembership.getOwnedRelatedElement().stream())
                .filter(ReferenceUsage.class::isInstance)
                .map(ReferenceUsage.class::cast)
                // Not getDeclaredName(): this feature is an implicit redefinition (e.g. "verificationCase = ...;"
                // inside a @Metadata block), so it has no declared name of its own -- only the derived,
                // effective getName(). See VTVQueryServices' getFeature() for the same gotcha.
                .filter(referenceUsage -> featureName.equals(referenceUsage.getName()))
                .findFirst();
    }
}
