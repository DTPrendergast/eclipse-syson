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
package org.eclipse.syson.ai.services.provenance;

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
import org.eclipse.syson.sysml.MetadataDefinition;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@code sysml.libraries/AiProvenance.json} is picked up by the standard-library
 * classpath scan and still defines all three of its attributes.
 *
 * <p>
 * The attribute assertions are the point of this test, not decoration. An attribute whose name
 * collides with a reserved word is dropped by the parser <em>silently</em> - no error, no warning -
 * and it takes every attribute declared after it in the same body with it. That is how the first
 * version of this library came to define a {@code Generated} metadata definition with no
 * attributes at all while every parse report stayed empty. If someone renames these to something
 * shorter and more natural like {@code by} or {@code at}, this test is what tells them, instead of
 * provenance quietly becoming an empty marker.
 * </p>
 *
 * @author DTPrendergast
 */
class AiProvenanceLibraryLoadingTest {

    @Test
    void generatedMetadataDefinitionIsLoadedWithEveryAttribute() {
        List<Resource> standardLibraries = new SysONDefaultLibrariesConfiguration(new SysONLoadDefaultLibrariesOnApplicationStartConfiguration())
                .getLibrariesResourceSet().getResources();

        Optional<MetadataDefinition> generated = standardLibraries.stream()
                .flatMap(resource -> this.allContents(resource).stream())
                .filter(MetadataDefinition.class::isInstance)
                .map(MetadataDefinition.class::cast)
                .filter(metadataDefinition -> "Generated".equals(metadataDefinition.getDeclaredName()))
                .findFirst();

        assertThat(generated).as("the AiProvenance library was not picked up by the classpath scan").isPresent();

        List<String> attributeNames = generated.get().getOwnedMember().stream()
                .filter(AttributeUsage.class::isInstance)
                .map(AttributeUsage.class::cast)
                .map(AttributeUsage::getDeclaredName)
                .toList();

        assertThat(attributeNames)
                .as("a reserved-word rename would drop these silently; see this class's javadoc")
                .containsExactlyInAnyOrder("generatedBy", "generatedAt", "proposalId");
    }

    private List<EObject> allContents(Resource resource) {
        Iterator<EObject> iterator = resource.getAllContents();
        Spliterator<EObject> spliterator = Spliterators.spliteratorUnknownSize(iterator, 0);
        return Stream.concat(resource.getContents().stream(), StreamSupport.stream(spliterator, false)).toList();
    }
}
