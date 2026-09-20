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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.syson.application.configuration.SysONDefaultLibrariesConfiguration;
import org.eclipse.syson.application.configuration.SysONLoadDefaultLibrariesOnApplicationStartConfiguration;
import org.eclipse.syson.sysml.ASTTransformer;
import org.eclipse.syson.sysml.Documentation;
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.SysmlToAst;
import org.eclipse.syson.sysml.TextualRepresentation;
import org.eclipse.syson.sysml.metamodel.services.textual.SysMLElementSerializer;
import org.eclipse.syson.sysml.metamodel.services.textual.SysMLSerializingOptions;
import org.eclipse.syson.sysml.metamodel.services.textual.utils.FileNameDeresolver;
import org.junit.jupiter.api.Test;

/**
 * Tests of where an operational concept's narrative lives.
 *
 * @author DTPrendergast
 */
class OperationalConceptNarrativeServiceTests {

    /** The current scheme: a named markdown textual representation on the concept itself. */
    private static final String WITH_REPRESENTATION = """
            package DispatchOperations {
                doc /* Everything to do with outage dispatch. */
                @OperationalConceptLibrary::OperationalConcept;
                rep conopsNarrative language "markdown" /* # Introduction */
            }
            """;

    /** The previous scheme: documentation of the annotation. */
    private static final String LEGACY = """
            package DispatchOperations {
                doc /* Everything to do with outage dispatch. */
                @OperationalConceptLibrary::OperationalConcept {
                    doc /* legacy narrative */
                }
            }
            """;

    private static final String NOT_A_CONCEPT = "package Plain { part x; }";

    private static final String CONCEPT_NAME = "DispatchOperations";

    private final OperationalConceptNarrativeService service = new OperationalConceptNarrativeService();

    @Test
    void readsTheNarrativeFromItsRepresentation() {
        assertThat(this.service.getNarrative(this.findPackage(WITH_REPRESENTATION, CONCEPT_NAME)))
                .contains("# Introduction");
    }

    @Test
    void stillReadsContentLeftByThePreviousScheme() {
        // Existing projects have their narrative on the annotation; it must not look lost.
        assertThat(this.service.getNarrative(this.findPackage(LEGACY, CONCEPT_NAME)))
                .contains("legacy narrative");
    }

    @Test
    void writingMovesLegacyContentToTheRepresentationAndRemovesTheOldCopy() {
        Element concept = this.findPackage(LEGACY, CONCEPT_NAME);

        this.service.setNarrative(concept, "# Rewritten");

        assertThat(this.service.getNarrative(concept)).isEqualTo("# Rewritten");
        assertThat(this.narrativeRepresentation(concept)).isNotNull();
        // One source of truth: two copies would drift apart, and the old one does not export.
        assertThat(this.annotationDocumentationCount(concept)).isZero();
    }

    @Test
    void theNarrativeSurvivesTextualExport() {
        Element concept = this.findPackage(LEGACY, CONCEPT_NAME);

        this.service.setNarrative(concept, "# Introduction\n\nThe dispatcher triages.");

        // The reason for the move: applied metadata is dropped by the serializer, so a narrative
        // stored on the annotation vanished on export without saying so.
        String exported = this.export(concept);
        assertThat(exported).contains("rep conopsNarrative");
        assertThat(exported).contains("language \"markdown\"");
        assertThat(exported).contains("The dispatcher triages.");
    }

    @Test
    void theConceptsOwnDocumentationIsLeftAlone() {
        Element concept = this.findPackage(WITH_REPRESENTATION, CONCEPT_NAME);

        this.service.setNarrative(concept, "rewritten");

        assertThat(this.export(concept)).contains("Everything to do with outage dispatch.");
    }

    @Test
    void editingTwiceLeavesOneRepresentation() {
        Element concept = this.findPackage(WITH_REPRESENTATION, CONCEPT_NAME);

        this.service.setNarrative(concept, "first");
        this.service.setNarrative(concept, "second");

        long count = concept.getOwnedElement().stream().filter(TextualRepresentation.class::isInstance).count();
        assertThat(count).isEqualTo(1);
        assertThat(this.service.getNarrative(concept)).isEqualTo("second");
    }

    @Test
    void refusesToWriteOntoSomethingThatIsNotAnOperationalConcept() {
        Element plain = this.findPackage(NOT_A_CONCEPT, "Plain");

        assertThat(this.service.setNarrative(plain, "nowhere to live")).isFalse();
        assertThat(this.service.getNarrative(plain)).isEmpty();
    }

    @Test
    void deletingTheMarkerNoLongerDestroysTheNarrative() {
        Element concept = this.findPackage(WITH_REPRESENTATION, CONCEPT_NAME);
        var annotation = this.service.findAnnotation(concept);

        // Simulate the user deleting what looks like a stray marker object in the explorer.
        concept.getOwnedRelationship().removeIf(relationship -> relationship.getOwnedRelatedElement().contains(annotation));

        assertThat(this.service.findAnnotation(concept)).isNull();
        assertThat(this.service.getNarrative(concept)).contains("# Introduction");
    }

    private TextualRepresentation narrativeRepresentation(Element element) {
        return element.getOwnedElement().stream()
                .filter(TextualRepresentation.class::isInstance)
                .map(TextualRepresentation.class::cast)
                .findFirst()
                .orElse(null);
    }

    private long annotationDocumentationCount(Element element) {
        var annotation = this.service.findAnnotation(element);
        return annotation.getOwnedElement().stream().filter(Documentation.class::isInstance).count();
    }

    private String export(Element element) {
        var options = new SysMLSerializingOptions.Builder()
                .lineSeparator("\n")
                .nameDeresolver(new FileNameDeresolver())
                .indentation("  ")
                .needEscapeCharacter(true)
                .build();
        return new SysMLElementSerializer(options, report -> { }).doSwitch(element);
    }

    private Element findPackage(String source, String name) {
        Element found = null;
        var iterator = this.parse(source).getAllContents();
        while (iterator.hasNext() && found == null) {
            var next = iterator.next();
            if (next instanceof org.eclipse.syson.sysml.Package packageElement && name.equals(packageElement.getDeclaredName())) {
                found = packageElement;
            }
        }
        if (found == null) {
            found = fail("No package named " + name);
        }
        return found;
    }

    private Resource parse(String source) {
        var resourceSet = new SysONDefaultLibrariesConfiguration(new SysONLoadDefaultLibrariesOnApplicationStartConfiguration()).getLibrariesResourceSet();
        var parsed = new SysmlToAst(null).convert(new ByteArrayInputStream(source.getBytes()), "sysml");
        if (parsed.ast().isEmpty()) {
            return fail("No AST: " + parsed.reports());
        }
        return new ASTTransformer().convertResource(parsed.ast().get(), resourceSet);
    }
}
