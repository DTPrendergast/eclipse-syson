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
package org.eclipse.syson.requirements.library.services;

import java.util.Optional;

import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.EnumerationUsage;
import org.eclipse.syson.sysml.FeatureReferenceExpression;
import org.eclipse.syson.sysml.LiteralString;
import org.eclipse.syson.sysml.MetadataUsage;
import org.eclipse.syson.sysml.OwningMembership;
import org.eclipse.syson.sysml.ReferenceUsage;
import org.eclipse.syson.sysml.RequirementUsage;
import org.eclipse.syson.sysml.metamodel.services.MetamodelQueryElementService;

/**
 * Reads the {@code ThresholdOrObjective} and {@code RequirementCategory} metadata (see
 * {@code RequirementAttributesLibrary}'s Javadoc) applied on a {@link RequirementUsage}.
 *
 * @author DTPrendergast
 */
public class RequirementAttributesQueryServices {

    private static final String THRESHOLD_OR_OBJECTIVE_METADATA = "ThresholdOrObjective";

    private static final String THRESHOLD_OR_OBJECTIVE_FEATURE = "kind";

    private static final String REQUIREMENT_CATEGORY_METADATA = "RequirementCategory";

    private static final String REQUIREMENT_CATEGORY_FEATURE = "category";

    // Not constructor-injected: MetamodelQueryElementService is a plain utility class, not a Spring bean -- see
    // VTVQueryServices' own comment on why that must not be a constructor parameter of a class Sirius Web
    // instantiates via AutowireCapableBeanFactory.createBean(Class).
    private final MetamodelQueryElementService metamodelQueryElementService = new MetamodelQueryElementService();

    public RequirementAttributesQueryServices() {
        // Empty constructor, made explicitly public per this module's coding rules (no package-private
        // constructors).
    }

    /**
     * Returns the {@code ThresholdOrObjectiveKind} literal ({@code "threshold"} or {@code "objective"}) applied
     * on the given requirement via the {@code ThresholdOrObjective} metadata, or an empty string if not set.
     */
    public String getThresholdOrObjective(RequirementUsage requirementUsage) {
        return this.getEnumLiteralName(requirementUsage, THRESHOLD_OR_OBJECTIVE_METADATA, THRESHOLD_OR_OBJECTIVE_FEATURE)
                .orElse("");
    }

    /**
     * Returns the free-text category applied on the given requirement via the {@code RequirementCategory}
     * metadata, or an empty string if not set.
     */
    public String getCategory(RequirementUsage requirementUsage) {
        return this.getStringFeatureValue(requirementUsage, REQUIREMENT_CATEGORY_METADATA, REQUIREMENT_CATEGORY_FEATURE)
                .orElse("");
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
                // Not getDeclaredName(): this feature is an implicit redefinition (e.g. "kind = ...;" inside a
                // @Metadata block), so it has no declared name of its own -- only the derived, effective getName().
                .filter(referenceUsage -> featureName.equals(referenceUsage.getName()))
                .findFirst();
    }

    private Optional<String> getEnumLiteralName(Element element, String metadataDefinitionName, String featureName) {
        return this.getAppliedMetadata(element, metadataDefinitionName)
                .flatMap(metadataUsage -> this.getFeature(metadataUsage, featureName))
                .flatMap(this.metamodelQueryElementService::getValueExpression)
                .filter(FeatureReferenceExpression.class::isInstance)
                .map(FeatureReferenceExpression.class::cast)
                .map(FeatureReferenceExpression::getReferent)
                .filter(EnumerationUsage.class::isInstance)
                .map(EnumerationUsage.class::cast)
                .map(EnumerationUsage::getDeclaredName);
    }

    private Optional<String> getStringFeatureValue(Element element, String metadataDefinitionName, String featureName) {
        return this.getAppliedMetadata(element, metadataDefinitionName)
                .flatMap(metadataUsage -> this.getFeature(metadataUsage, featureName))
                .flatMap(this.metamodelQueryElementService::getValueExpression)
                .filter(LiteralString.class::isInstance)
                .map(LiteralString.class::cast)
                .map(LiteralString::getValue);
    }
}
