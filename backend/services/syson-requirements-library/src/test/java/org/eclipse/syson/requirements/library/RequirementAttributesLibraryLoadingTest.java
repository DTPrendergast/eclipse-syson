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
package org.eclipse.syson.requirements.library;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.syson.application.configuration.SysONDefaultLibrariesConfiguration;
import org.eclipse.syson.application.configuration.SysONLoadDefaultLibrariesOnApplicationStartConfiguration;
import org.eclipse.syson.sysml.AttributeUsage;
import org.eclipse.syson.sysml.EnumerationDefinition;
import org.eclipse.syson.sysml.EnumerationUsage;
import org.eclipse.syson.sysml.MetadataDefinition;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@code sysml.libraries/RequirementAttributes.json} -- shipped on this module's classpath -- is
 * picked up by {@link SysONDefaultLibrariesConfiguration}'s classpath scan of the "SysML Standard Library"
 * family (see this module's README.md), and that it defines the expected {@code ThresholdOrObjectiveKind}
 * enumeration, {@code ThresholdOrObjective} metadata definition, and {@code RequirementCategory} metadata
 * definition.
 *
 * @author DTPrendergast
 */
class RequirementAttributesLibraryLoadingTest {

    private final List<Resource> standardLibraries = new SysONDefaultLibrariesConfiguration(new SysONLoadDefaultLibrariesOnApplicationStartConfiguration())
            .getLibrariesResourceSet().getResources();

    @Test
    void thresholdOrObjectiveKindEnumerationIsLoadedWithBothLiterals() {
        Optional<EnumerationDefinition> kind = this.findByDeclaredName(EnumerationDefinition.class, "ThresholdOrObjectiveKind");

        assertThat(kind).isPresent();
        List<String> literalNames = kind.get().getOwnedMember().stream()
                .filter(EnumerationUsage.class::isInstance)
                .map(EnumerationUsage.class::cast)
                .map(EnumerationUsage::getDeclaredName)
                .toList();
        assertThat(literalNames).containsExactlyInAnyOrder("threshold", "objective");
    }

    @Test
    void thresholdOrObjectiveMetadataDefinitionIsLoaded() {
        Optional<MetadataDefinition> thresholdOrObjective = this.findByDeclaredName(MetadataDefinition.class, "ThresholdOrObjective");

        assertThat(thresholdOrObjective).isPresent();
        boolean hasKindAttribute = thresholdOrObjective.get().getOwnedMember().stream()
                .anyMatch(member -> "kind".equals(member.getDeclaredName()));
        assertThat(hasKindAttribute).isTrue();
    }

    @Test
    void requirementCategoryMetadataDefinitionIsLoaded() {
        Optional<MetadataDefinition> requirementCategory = this.findByDeclaredName(MetadataDefinition.class, "RequirementCategory");

        assertThat(requirementCategory).isPresent();
        boolean hasCategoryAttribute = requirementCategory.get().getOwnedMember().stream()
                .filter(AttributeUsage.class::isInstance)
                .map(AttributeUsage.class::cast)
                .anyMatch(attribute -> "category".equals(attribute.getDeclaredName()));
        assertThat(hasCategoryAttribute).isTrue();
    }

    @Test
    void requirementIdSequenceMetadataDefinitionIsLoadedWithBothAttributes() {
        Optional<MetadataDefinition> requirementIdSequence = this.findByDeclaredName(MetadataDefinition.class, "RequirementIdSequence");

        assertThat(requirementIdSequence).isPresent();
        List<String> attributeNames = requirementIdSequence.get().getOwnedMember().stream()
                .filter(AttributeUsage.class::isInstance)
                .map(AttributeUsage.class::cast)
                .map(AttributeUsage::getDeclaredName)
                .toList();
        assertThat(attributeNames).containsExactlyInAnyOrder("prefix", "nextNumber");
    }

    private <T> Optional<T> findByDeclaredName(Class<T> type, String declaredName) {
        return this.standardLibraries.stream()
                .flatMap(resource -> this.allContents(resource).stream())
                .filter(type::isInstance)
                .map(type::cast)
                .filter(element -> declaredName.equals(((org.eclipse.syson.sysml.Element) element).getDeclaredName()))
                .findFirst();
    }

    private List<EObject> allContents(Resource resource) {
        Iterator<EObject> iterator = resource.getAllContents();
        Spliterator<EObject> spliterator = Spliterators.spliteratorUnknownSize(iterator, 0);
        return Stream.concat(resource.getContents().stream(), StreamSupport.stream(spliterator, false)).toList();
    }
}
