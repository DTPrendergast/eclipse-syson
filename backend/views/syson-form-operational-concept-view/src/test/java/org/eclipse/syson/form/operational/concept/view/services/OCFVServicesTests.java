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
package org.eclipse.syson.form.operational.concept.view.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.syson.application.configuration.SysONDefaultLibrariesConfiguration;
import org.eclipse.syson.application.configuration.SysONLoadDefaultLibrariesOnApplicationStartConfiguration;
import org.eclipse.syson.sysml.ASTTransformer;
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.Package;
import org.eclipse.syson.sysml.SysmlToAst;
import org.junit.jupiter.api.Test;

/**
 * Round-trip tests of the operational concept narrative editor's services.
 *
 * <p>
 * These parse real textual SysML so the structure being read and written is the one the parser
 * actually produces, rather than a hand-built approximation of it.
 * </p>
 *
 * @author DTPrendergast
 */
class OCFVServicesTests {

    private static final String WITH_NARRATIVE = """
            package DispatchOperations {
                doc /* Everything to do with outage dispatch. */
                @OperationalConceptLibrary::OperationalConcept;
                rep conopsNarrative language "markdown" /* Dispatchers coordinate field crews during an outage. */
                part dispatcher;
            }
            """;

    private static final String WITHOUT_DOC = """
            package PlainPackage {
                part somePart;
            }
            """;

    private final OCFVQueryServices queryServices = new OCFVQueryServices();

    private final OCFVMutationServices mutationServices = new OCFVMutationServices();

    @Test
    void readsTheNarrativeBack() {
        Element concept = this.findPackage(WITH_NARRATIVE, "DispatchOperations");

        assertThat(this.queryServices.getNarrative(concept))
                .contains("Dispatchers coordinate field crews during an outage.");
    }

    @Test
    void theNarrativeAndThePackageDocumentationAreSeparateFields() {
        Element concept = this.findPackage(WITH_NARRATIVE, "DispatchOperations");

        // The whole point of storing the narrative on the annotation: a package's own doc says what
        // the package is, the narrative says who uses the system and how, and editing one must not
        // touch the other.
        assertThat(this.queryServices.getDocumentationBody(concept)).isEqualTo("Everything to do with outage dispatch.");

        this.mutationServices.setNarrative(concept, "rewritten narrative");

        assertThat(this.queryServices.getNarrative(concept)).isEqualTo("rewritten narrative");
        assertThat(this.queryServices.getDocumentationBody(concept)).isEqualTo("Everything to do with outage dispatch.");
    }

    @Test
    void anElementWithoutANarrativeReadsAsEmptyRatherThanNull() {
        Element plain = this.findPackage(WITHOUT_DOC, "PlainPackage");

        // The widget binds this straight to its value, so null here would surface as a broken
        // editor rather than an empty one.
        assertThat(this.queryServices.getNarrative(plain)).isEmpty();
    }

    @Test
    void writesTheNarrativeBack() {
        Element concept = this.findPackage(WITH_NARRATIVE, "DispatchOperations");

        this.mutationServices.setNarrative(concept, "# Revised\n\nDispatchers triage, then dispatch.");

        assertThat(this.queryServices.getNarrative(concept)).isEqualTo("# Revised\n\nDispatchers triage, then dispatch.");
    }

    @Test
    void editingReplacesTheBodyRatherThanAddingASecondDocumentation() {
        Element concept = this.findPackage(WITH_NARRATIVE, "DispatchOperations");

        this.mutationServices.setNarrative(concept, "first edit");
        this.mutationServices.setNarrative(concept, "second edit");

        // Two narratives on one concept would both be valid SysML and would export as two reps,
        // so this is the assertion that keeps editing idempotent.
        long narrativeCount = concept.getOwnedElement().stream()
                .filter(org.eclipse.syson.sysml.TextualRepresentation.class::isInstance)
                .count();
        assertThat(narrativeCount).isEqualTo(1);
        assertThat(this.queryServices.getNarrative(concept)).isEqualTo("second edit");
    }

    @Test
    void writesNothingWhenTheElementIsNotAnOperationalConcept() {
        Element plain = this.findPackage(WITHOUT_DOC, "PlainPackage");

        this.mutationServices.setNarrative(plain, "narrative with nowhere to live");

        // Writing it onto the element itself would put it where nothing reads it back from, and
        // would silently overwrite the element's own documentation.
        assertThat(this.queryServices.getNarrative(plain)).isEmpty();
        assertThat(this.queryServices.getDocumentationBody(plain)).isEmpty();
    }

    @Test
    void recognisesAnOperationalConceptByItsMetadata() {
        assertThat(this.queryServices.isOperationalConcept(this.findPackage(WITH_NARRATIVE, "DispatchOperations"))).isTrue();
        assertThat(this.queryServices.isOperationalConcept(this.findPackage(WITHOUT_DOC, "PlainPackage"))).isFalse();
    }

    @Test
    void aNullEditIsTreatedAsClearingTheNarrative() {
        Element concept = this.findPackage(WITH_NARRATIVE, "DispatchOperations");

        this.mutationServices.setNarrative(concept, null);

        assertThat(this.queryServices.getNarrative(concept)).isEmpty();
    }

    private Element findPackage(String source, String declaredName) {
        Element found = null;
        var iterator = this.parseResource(source).getAllContents();
        while (iterator.hasNext() && found == null) {
            var next = iterator.next();
            if (next instanceof Package packageElement && declaredName.equals(packageElement.getDeclaredName())) {
                found = packageElement;
            }
        }
        if (found == null) {
            found = fail("No package named " + declaredName);
        }
        return found;
    }

    private Resource parseResource(String source) {
        ResourceSet resourceSet = new SysONDefaultLibrariesConfiguration(new SysONLoadDefaultLibrariesOnApplicationStartConfiguration())
                .getLibrariesResourceSet();
        var astParsingResult = new SysmlToAst(null).convert(new ByteArrayInputStream(source.getBytes()), "sysml");
        if (astParsingResult.ast().isEmpty()) {
            return fail("No AST produced: " + astParsingResult.reports());
        }
        return new ASTTransformer().convertResource(astParsingResult.ast().get(), resourceSet);
    }
}
