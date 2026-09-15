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

    private static final String WITH_DOC = """
            package DispatchOperations {
                @OperationalConceptLibrary::OperationalConcept;
                doc /* Dispatchers coordinate field crews during an outage. */
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
        Element concept = this.findPackage(WITH_DOC, "DispatchOperations");

        assertThat(this.queryServices.getDocumentationBody(concept))
                .contains("Dispatchers coordinate field crews during an outage.");
    }

    @Test
    void anElementWithoutDocumentationReadsAsEmptyRatherThanNull() {
        Element plain = this.findPackage(WITHOUT_DOC, "PlainPackage");

        // The widget binds this straight to its value, so null here would surface as a broken
        // editor rather than an empty one.
        assertThat(this.queryServices.getDocumentationBody(plain)).isEmpty();
    }

    @Test
    void writesTheNarrativeBack() {
        Element concept = this.findPackage(WITH_DOC, "DispatchOperations");

        this.mutationServices.setDocumentationBody(concept, "# Revised\n\nDispatchers triage, then dispatch.");

        assertThat(this.queryServices.getDocumentationBody(concept)).isEqualTo("# Revised\n\nDispatchers triage, then dispatch.");
    }

    @Test
    void editingReplacesTheBodyRatherThanAddingASecondDocumentation() {
        Element concept = this.findPackage(WITH_DOC, "DispatchOperations");

        this.mutationServices.setDocumentationBody(concept, "first edit");
        this.mutationServices.setDocumentationBody(concept, "second edit");

        // Two doc bodies on one element would both be valid SysML and would export as two doc
        // blocks, so this is the assertion that keeps editing idempotent.
        long documentationCount = concept.getOwnedElement().stream()
                .filter(org.eclipse.syson.sysml.Documentation.class::isInstance)
                .count();
        assertThat(documentationCount).isEqualTo(1);
        assertThat(this.queryServices.getDocumentationBody(concept)).isEqualTo("second edit");
    }

    @Test
    void createsTheDocumentationWhenTheElementHasNoneYet() {
        Element plain = this.findPackage(WITHOUT_DOC, "PlainPackage");

        this.mutationServices.setDocumentationBody(plain, "newly authored narrative");

        assertThat(this.queryServices.getDocumentationBody(plain)).isEqualTo("newly authored narrative");
    }

    @Test
    void recognisesAnOperationalConceptByItsMetadata() {
        assertThat(this.queryServices.isOperationalConcept(this.findPackage(WITH_DOC, "DispatchOperations"))).isTrue();
        assertThat(this.queryServices.isOperationalConcept(this.findPackage(WITHOUT_DOC, "PlainPackage"))).isFalse();
    }

    @Test
    void aNullEditIsTreatedAsClearingTheNarrative() {
        Element concept = this.findPackage(WITH_DOC, "DispatchOperations");

        this.mutationServices.setDocumentationBody(concept, null);

        assertThat(this.queryServices.getDocumentationBody(concept)).isEmpty();
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
