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
package org.eclipse.syson.operational.concept.library;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.sirius.components.representations.MessageLevel;
import org.eclipse.sirius.emfjson.resource.JsonResource;
import org.eclipse.syson.application.configuration.SysONDefaultLibrariesConfiguration;
import org.eclipse.syson.application.configuration.SysONLoadDefaultLibrariesOnApplicationStartConfiguration;
import org.eclipse.syson.sysml.ASTTransformer;
import org.eclipse.syson.sysml.AstParsingResult;
import org.eclipse.syson.sysml.SysmlToAst;
import org.eclipse.syson.sysml.metamodel.services.textual.utils.Severity;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Not part of the automated test suite: this is a one-off developer utility that (re)generates
 * {@code src/main/resources/sysml.libraries/OperationalConcept.json} -- the file actually loaded at runtime --
 * from the canonical, human-authored
 * {@code src/main/resources/operational-concept-library-source/OperationalConcept.sysml} source.
 *
 * <p>
 * See {@code VerificationProcedureLibraryGenerator} in {@code syson-verification-library} for why this is
 * deliberately excluded from CI (fresh internal ids on every run) and how to run it.
 * </p>
 *
 * @author DTPrendergast
 */
class OperationalConceptLibraryGenerator {

    private static final Path SOURCE_PATH = Path.of("src/main/resources/operational-concept-library-source/OperationalConcept.sysml");

    private static final Path OUTPUT_PATH = Path.of("src/main/resources/sysml.libraries/OperationalConcept.json");

    @Test
    @Disabled("Run manually to regenerate OperationalConcept.json; deliberately excluded from CI, see class javadoc")
    void generate() throws IOException {
        ResourceSet resourceSet = new SysONDefaultLibrariesConfiguration(new SysONLoadDefaultLibrariesOnApplicationStartConfiguration()).getLibrariesResourceSet();

        byte[] sourceBytes = Files.readAllBytes(SOURCE_PATH);

        SysmlToAst sysmlToAst = new SysmlToAst(null);
        AstParsingResult astParsingResult = sysmlToAst.convert(new ByteArrayInputStream(sourceBytes), "sysml");
        assertTrue(astParsingResult.reports().stream().noneMatch(status -> status.severity() == Severity.ERROR),
                () -> "Errors while parsing " + SOURCE_PATH + ": " + astParsingResult.reports());
        assertTrue(astParsingResult.ast().isPresent(), "syside-cli produced no AST for " + SOURCE_PATH);

        ASTTransformer transformer = new ASTTransformer();
        Resource resource = transformer.convertResource(astParsingResult.ast().get(), resourceSet);
        assertNotNull(resource, "AST-to-model conversion produced no resource");
        assertFalse(transformer.getTransformationMessages().stream().anyMatch(message -> message.level() == MessageLevel.ERROR),
                () -> "Errors while converting AST to model: " + transformer.getTransformationMessages().stream()
                        .map(message -> message.body()).collect(Collectors.joining("; ")));
        assertFalse(resource.getContents().isEmpty(), "Conversion produced an empty resource");

        Map<String, Object> saveOptions = new HashMap<>();
        saveOptions.put(JsonResource.OPTION_FORCE_DEFAULT_REFERENCE_SERIALIZATION, Boolean.TRUE);

        Files.createDirectories(OUTPUT_PATH.getParent());
        try (OutputStream outputStream = Files.newOutputStream(OUTPUT_PATH)) {
            resource.save(outputStream, saveOptions);
        }
    }
}
