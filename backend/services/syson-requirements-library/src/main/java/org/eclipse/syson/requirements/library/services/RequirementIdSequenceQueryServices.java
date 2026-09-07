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
import org.eclipse.syson.sysml.LiteralInteger;
import org.eclipse.syson.sysml.LiteralString;
import org.eclipse.syson.sysml.MetadataUsage;
import org.eclipse.syson.sysml.OwningMembership;
import org.eclipse.syson.sysml.ReferenceUsage;
import org.eclipse.syson.sysml.metamodel.services.MetamodelQueryElementService;

/**
 * Reads the {@code RequirementIdSequence} metadata (see {@code RequirementAttributesLibrary}'s Javadoc) applied
 * on an element -- typically the requirements table's owning namespace, not a requirement itself.
 *
 * <p>
 * Unlike {@link RequirementAttributesQueryServices}, {@link #getNextNumberLiteral(Element)} returns the raw
 * {@link LiteralInteger}, not its {@code int} value: the caller (in a different module,
 * {@code syson-table-requirements-view}) needs to mutate it in place after reading it, to increment the counter
 * without a textual reimport -- see that module's {@code RTVMutationServices} for why (avoids re-deriving the
 * metadata's redefinition structure for what is otherwise a single {@code EAttribute} flip).
 * </p>
 *
 * @author DTPrendergast
 */
public class RequirementIdSequenceQueryServices {

    private static final String REQUIREMENT_ID_SEQUENCE_METADATA = "RequirementIdSequence";

    private static final String PREFIX_FEATURE = "prefix";

    private static final String NEXT_NUMBER_FEATURE = "nextNumber";

    // Not constructor-injected: MetamodelQueryElementService is a plain utility class, not a Spring bean -- see
    // VTVQueryServices' own comment on why that must not be a constructor parameter of a class Sirius Web
    // instantiates via AutowireCapableBeanFactory.createBean(Class).
    private final MetamodelQueryElementService metamodelQueryElementService = new MetamodelQueryElementService();

    public RequirementIdSequenceQueryServices() {
        // Empty constructor, made explicitly public per this module's coding rules (no package-private
        // constructors).
    }

    /**
     * Returns the {@code prefix} applied via {@code RequirementIdSequence} on the given element, or empty if the
     * metadata is not applied.
     */
    public Optional<String> getPrefix(Element element) {
        return this.getAppliedMetadata(element)
                .flatMap(metadataUsage -> this.getFeature(metadataUsage, PREFIX_FEATURE))
                .flatMap(this.metamodelQueryElementService::getValueExpression)
                .filter(LiteralString.class::isInstance)
                .map(LiteralString.class::cast)
                .map(LiteralString::getValue);
    }

    /**
     * Returns the raw {@code nextNumber} {@link LiteralInteger} applied via {@code RequirementIdSequence} on the
     * given element, or empty if the metadata is not applied. Returned as the live literal object (not its
     * {@code int} value) so the caller can increment it in place.
     */
    public Optional<LiteralInteger> getNextNumberLiteral(Element element) {
        return this.getAppliedMetadata(element)
                .flatMap(metadataUsage -> this.getFeature(metadataUsage, NEXT_NUMBER_FEATURE))
                .flatMap(this.metamodelQueryElementService::getValueExpression)
                .filter(LiteralInteger.class::isInstance)
                .map(LiteralInteger.class::cast);
    }

    private Optional<MetadataUsage> getAppliedMetadata(Element element) {
        return element.getOwnedRelationship().stream()
                .filter(OwningMembership.class::isInstance)
                .map(OwningMembership.class::cast)
                .flatMap(owningMembership -> owningMembership.getOwnedRelatedElement().stream())
                .filter(MetadataUsage.class::isInstance)
                .map(MetadataUsage.class::cast)
                .filter(metadataUsage -> metadataUsage.getMetadataDefinition() != null
                        && REQUIREMENT_ID_SEQUENCE_METADATA.equals(metadataUsage.getMetadataDefinition().getName()))
                .findFirst();
    }

    private Optional<ReferenceUsage> getFeature(MetadataUsage metadataUsage, String featureName) {
        return metadataUsage.getOwnedRelationship().stream()
                .filter(OwningMembership.class::isInstance)
                .map(OwningMembership.class::cast)
                .flatMap(owningMembership -> owningMembership.getOwnedRelatedElement().stream())
                .filter(ReferenceUsage.class::isInstance)
                .map(ReferenceUsage.class::cast)
                // Not getDeclaredName(): this feature is an implicit redefinition (e.g. "prefix = ...;" inside a
                // @Metadata block), so it has no declared name of its own -- only the derived, effective getName().
                .filter(referenceUsage -> featureName.equals(referenceUsage.getName()))
                .findFirst();
    }
}
