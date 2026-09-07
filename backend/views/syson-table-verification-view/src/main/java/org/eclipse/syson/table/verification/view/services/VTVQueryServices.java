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

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.sirius.components.tables.ColumnFilter;
import org.eclipse.sirius.components.tables.ColumnSort;
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.EnumerationUsage;
import org.eclipse.syson.sysml.FeatureReferenceExpression;
import org.eclipse.syson.sysml.LiteralString;
import org.eclipse.syson.sysml.MetadataUsage;
import org.eclipse.syson.sysml.OwningMembership;
import org.eclipse.syson.sysml.ReferenceUsage;
import org.eclipse.syson.sysml.VerificationCaseUsage;
import org.eclipse.syson.sysml.metamodel.services.MetamodelQueryElementService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

/**
 * Query services for the Verification Table View. The services declared in this class must not modify the model or
 * the table view.
 *
 * <p>
 * Reads two pieces of applied metadata on a {@link VerificationCaseUsage}: the standard library's own
 * {@code VerificationMethod} (attribute {@code kind}, an enum reference) and this project's
 * {@code VerificationProcedureLibrary::VerificationProcedure} (attribute {@code procedureId}, a plain string). See
 * {@code syson-verification-library}'s README for why only {@code procedureId} needed adding.
 * </p>
 *
 * @author DTPrendergast
 */
public class VTVQueryServices {

    private static final String VERIFICATION_METHOD_METADATA = "VerificationMethod";

    private static final String VERIFICATION_METHOD_FEATURE = "kind";

    private static final String VERIFICATION_PROCEDURE_METADATA = "VerificationProcedure";

    private static final String VERIFICATION_PROCEDURE_FEATURE = "procedureId";

    // Not constructor-injected: MetamodelQueryElementService is a plain utility class (by design, per its own
    // Javadoc: "should not depend on sirius-web services or other spring services"), not a Spring bean. Sirius
    // Web instantiates AQL service classes like this one via AutowireCapableBeanFactory.createBean(Class), which
    // resolves constructor parameters by bean lookup -- a constructor parameter of a non-bean type fails at
    // runtime with UnsatisfiedDependencyException the moment a user opens the representation, even though it
    // compiles fine and passes hand-written unit tests that just call `new` directly. Caught by
    // ServiceClassInstantiationTests in syson-application.
    private final MetamodelQueryElementService metamodelQueryElementService = new MetamodelQueryElementService();

    private final ObjectMapper objectMapper;

    private final VTVKindMapping kindMapping = new VTVKindMapping();

    public VTVQueryServices(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    /**
     * Returns the {@code VerificationCaseUsage}s exposed in the table's {@code ViewUsage}.
     *
     * @param viewUsage
     *            the {@code ViewUsage} containing the table.
     * @return the exposed {@code VerificationCaseUsage}s.
     */
    public List<VerificationCaseUsage> getExposedVerificationCases(org.eclipse.syson.sysml.ViewUsage viewUsage) {
        return viewUsage.getExposedElement().stream()
                .filter(VerificationCaseUsage.class::isInstance)
                .map(VerificationCaseUsage.class::cast)
                .toList();
    }

    /**
     * Returns the display label of the verification method applied on the given verification case (one of
     * {@code Test}, {@code Demonstration}, {@code Analysis}, {@code Inspection}), or an empty string if the standard
     * {@code VerificationMethod} metadata is not applied.
     *
     * @param verificationCaseUsage
     *            a {@code VerificationCaseUsage}.
     * @return the display label of the applied verification method, or {@code ""}.
     */
    public String getVerificationMethodLabel(VerificationCaseUsage verificationCaseUsage) {
        return this.getEnumLiteralName(verificationCaseUsage, VERIFICATION_METHOD_METADATA, VERIFICATION_METHOD_FEATURE)
                .map(this.kindMapping::literalToLabel)
                .orElse("");
    }

    /**
     * Returns the procedure ID applied on the given verification case via this project's
     * {@code VerificationProcedure} metadata, or an empty string if not set.
     *
     * @param verificationCaseUsage
     *            a {@code VerificationCaseUsage}.
     * @return the procedure ID, or {@code ""}.
     */
    public String getProcedureId(VerificationCaseUsage verificationCaseUsage) {
        return this.getStringFeatureValue(verificationCaseUsage, VERIFICATION_PROCEDURE_METADATA, VERIFICATION_PROCEDURE_FEATURE)
                .orElse("");
    }

    public List<VerificationCaseUsage> sortAndFilterVerificationCases(List<Object> objects, List<ColumnSort> columnSort, List<ColumnFilter> columnFilters, String globalFilterData) {
        var verificationCases = objects.stream().filter(VerificationCaseUsage.class::isInstance).map(VerificationCaseUsage.class::cast).collect(Collectors.toList());
        this.filterVerificationCases(columnFilters, globalFilterData, verificationCases);
        this.sortVerificationCases(columnSort, verificationCases);
        return verificationCases;
    }

    private void filterVerificationCases(List<ColumnFilter> columnFilters, String globalFilterData, List<VerificationCaseUsage> verificationCases) {
        if (globalFilterData != null && !globalFilterData.isBlank()) {
            verificationCases.removeIf(vc -> !this.isValidGlobalFilterCandidate(vc, globalFilterData));
        }
        for (ColumnFilter columnFilter : columnFilters) {
            if ("DeclaredName".equals(columnFilter.id())) {
                verificationCases.removeIf(vc -> !this.isValidColumnFilterCandidate(vc.getDeclaredName(), columnFilter));
            } else if ("VerificationMethod".equals(columnFilter.id())) {
                verificationCases.removeIf(vc -> !this.isValidColumnFilterCandidate(this.getVerificationMethodLabel(vc), columnFilter));
            } else if ("ProcedureId".equals(columnFilter.id())) {
                verificationCases.removeIf(vc -> !this.isValidColumnFilterCandidate(this.getProcedureId(vc), columnFilter));
            }
        }
    }

    private void sortVerificationCases(List<ColumnSort> columnSort, List<VerificationCaseUsage> verificationCases) {
        for (ColumnSort colSort : columnSort) {
            Comparator<VerificationCaseUsage> comparator = switch (colSort.id()) {
                case "DeclaredName" -> Comparator.comparing(VerificationCaseUsage::getDeclaredName, String.CASE_INSENSITIVE_ORDER);
                case "VerificationMethod" -> Comparator.comparing(this::getVerificationMethodLabel, String.CASE_INSENSITIVE_ORDER);
                case "ProcedureId" -> Comparator.comparing(this::getProcedureId, String.CASE_INSENSITIVE_ORDER);
                default -> null;
            };
            if (comparator != null) {
                if (colSort.desc()) {
                    comparator = comparator.reversed();
                }
                verificationCases.sort(comparator);
            }
        }
    }

    private boolean isValidColumnFilterCandidate(String candidate, ColumnFilter columnFilter) {
        var isValid = true;
        try {
            JavaType javaType = this.objectMapper.getTypeFactory().constructType(String.class);
            String filterValue = this.objectMapper.readValue(columnFilter.value(), javaType);
            isValid = candidate != null && candidate.contains(filterValue);
        } catch (JacksonException exception) {
            // Malformed filter value: treat as "no filtering" rather than failing the whole table.
            isValid = true;
        }
        return isValid;
    }

    private boolean isValidGlobalFilterCandidate(VerificationCaseUsage verificationCaseUsage, String globalFilterData) {
        return (verificationCaseUsage.getDeclaredName() != null && verificationCaseUsage.getDeclaredName().contains(globalFilterData))
                || this.getVerificationMethodLabel(verificationCaseUsage).contains(globalFilterData)
                || this.getProcedureId(verificationCaseUsage).contains(globalFilterData);
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
