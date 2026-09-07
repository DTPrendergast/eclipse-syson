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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * {@code src/main/resources/sysml.libraries/RequirementAttributes.json} -- the file actually loaded at runtime
 * -- from the canonical, human-authored
 * {@code src/main/resources/requirements-library-source/RequirementAttributes.sysml} source.
 *
 * <p>
 * It is deliberately not run automatically: the underlying EMF-JSON serialization assigns fresh random
 * internal ids on every run, so re-running it produces a different byte-for-byte file even with no semantic
 * change, which would make every CI build report a spurious diff.
 * </p>
 *
 * <p>
 * Run manually whenever {@code RequirementAttributes.sysml} changes, e.g.:
 * </p>
 *
 * <pre>
 * mvn test -pl backend/services/syson-requirements-library \
 *     -Dtest=RequirementAttributesLibraryGenerator#generate -DfailIfNoTests=false
 * </pre>
 *
 * <p>
 * then review and commit the regenerated {@code RequirementAttributes.json}.
 * </p>
 *
 * @author DTPrendergast
 */
class RequirementAttributesLibraryGenerator {

    private static final Path SOURCE_PATH = Path.of("src/main/resources/requirements-library-source/RequirementAttributes.sysml");

    private static final Path OUTPUT_PATH = Path.of("src/main/resources/sysml.libraries/RequirementAttributes.json");

    @Test
    @Disabled("Run manually to regenerate RequirementAttributes.json; deliberately excluded from CI, see class javadoc")
    void generate() throws IOException {
        // Load KerML + SysML standard libraries into the same resource set used for conversion, so that
        // cross-references to ScalarValues::String and SysML::RequirementUsage resolve.
        ResourceSet resourceSet = new SysONDefaultLibrariesConfiguration(new SysONLoadDefaultLibrariesOnApplicationStartConfiguration()).getLibrariesResourceSet();

        byte[] sourceBytes = Files.readAllBytes(SOURCE_PATH);

        SysmlToAst sysmlToAst = new SysmlToAst(null);
        AstParsingResult astParsingResult = sysmlToAst.convert(new java.io.ByteArrayInputStream(sourceBytes), "sysml");
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
        // Match StandardLibrariesLoader: cross-references to other library files must serialize using the
        // stable elementId rather than a run-local EMF-JSON id, since they are resolved against a freshly
        // loaded (and therefore differently-id'd) copy of the standard libraries at application start.
        saveOptions.put(JsonResource.OPTION_FORCE_DEFAULT_REFERENCE_SERIALIZATION, Boolean.TRUE);

        Files.createDirectories(OUTPUT_PATH.getParent());
        try (OutputStream outputStream = Files.newOutputStream(OUTPUT_PATH)) {
            resource.save(outputStream, saveOptions);
        }
    }
}
