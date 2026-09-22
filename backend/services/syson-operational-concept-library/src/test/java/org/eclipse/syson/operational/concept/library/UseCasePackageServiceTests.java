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
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.SysmlToAst;
import org.eclipse.syson.sysml.metamodel.services.textual.SysMLElementSerializer;
import org.eclipse.syson.sysml.metamodel.services.textual.SysMLSerializingOptions;
import org.eclipse.syson.sysml.metamodel.services.textual.utils.FileNameDeresolver;
import org.junit.jupiter.api.Test;

/**
 * Tests of the package generated use cases are collected in.
 *
 * @author DTPrendergast
 */
class UseCasePackageServiceTests {

    private static final String CONCEPT = """
            package DispatchOperations {
                @OperationalConceptLibrary::OperationalConcept;
                rep conopsNarrative language "markdown" /* # Introduction */
            }
            """;

    private static final String CONCEPT_WITH_PACKAGE = """
            package DispatchOperations {
                @OperationalConceptLibrary::OperationalConcept;
                package 'Use Cases' {
                    use case restorePower;
                }
            }
            """;

    private final UseCasePackageService service = new UseCasePackageService();

    @Test
    void createsTheUseCasePackageWhenTheConceptHasNone() {
        Element concept = this.findPackage(CONCEPT, "DispatchOperations");

        assertThat(this.service.find(concept)).isNull();
        var created = this.service.findOrCreate(concept);

        assertThat(created).isNotNull();
        assertThat(created.getDeclaredName()).isEqualTo("Use Cases");
        assertThat(concept.getOwnedElement()).contains(created);
    }

    @Test
    void reusesTheUseCasePackageWhenItIsAlreadyThere() {
        Element concept = this.findPackage(CONCEPT_WITH_PACKAGE, "DispatchOperations");

        var existing = this.service.find(concept);
        assertThat(existing).isNotNull();

        assertThat(this.service.findOrCreate(concept)).isSameAs(existing);
        assertThat(concept.getOwnedElement().stream().filter(owned -> "Use Cases".equals(owned.getDeclaredName())).count()).isEqualTo(1);
    }

    /**
     * The name has a space in it, which is not a legal SysML identifier on its own. If the
     * serializer did not quote it the whole CONOPS package would export as invalid SysML - and the
     * point of this model is that it leaves the tool and is read by something else.
     */
    @Test
    void theNameWithASpaceSurvivesExportAndReparses() {
        Element concept = this.findPackage(CONCEPT, "DispatchOperations");
        this.service.findOrCreate(concept);

        String exported = this.export(concept);

        assertThat(exported).contains("'Use Cases'");
        assertThat(this.parse(exported)).isNotNull();
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
