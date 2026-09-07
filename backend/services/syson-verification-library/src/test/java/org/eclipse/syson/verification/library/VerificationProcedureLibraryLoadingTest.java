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
package org.eclipse.syson.verification.library;

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
 * Verifies that {@code sysml.libraries/VerificationProcedure.json} -- shipped on this module's classpath --
 * is picked up by {@link SysONDefaultLibrariesConfiguration}'s classpath scan of the "SysML Standard
 * Library" family (see this module's README.md), and that it defines the expected
 * {@code VerificationProcedure} metadata definition.
 *
 * @author DTPrendergast
 */
class VerificationProcedureLibraryLoadingTest {

    @Test
    void verificationProcedureMetadataDefinitionIsLoaded() {
        List<Resource> standardLibraries = new SysONDefaultLibrariesConfiguration(new SysONLoadDefaultLibrariesOnApplicationStartConfiguration())
                .getLibrariesResourceSet().getResources();

        Optional<MetadataDefinition> verificationProcedure = standardLibraries.stream()
                .flatMap(resource -> this.allContents(resource).stream())
                .filter(MetadataDefinition.class::isInstance)
                .map(MetadataDefinition.class::cast)
                .filter(metadataDefinition -> "VerificationProcedure".equals(metadataDefinition.getDeclaredName()))
                .findFirst();

        assertThat(verificationProcedure).isPresent();

        boolean hasProcedureIdAttribute = verificationProcedure.get().getOwnedMember().stream()
                .filter(AttributeUsage.class::isInstance)
                .map(AttributeUsage.class::cast)
                .anyMatch(attribute -> "procedureId".equals(attribute.getDeclaredName()));
        assertThat(hasProcedureIdAttribute).isTrue();
    }

    private List<EObject> allContents(Resource resource) {
        Iterator<EObject> iterator = resource.getAllContents();
        Spliterator<EObject> spliterator = Spliterators.spliteratorUnknownSize(iterator, 0);
        return Stream.concat(resource.getContents().stream(), StreamSupport.stream(spliterator, false)).toList();
    }
}
