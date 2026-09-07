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
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.syson.application.configuration.SysONDefaultLibrariesConfiguration;
import org.eclipse.syson.application.configuration.SysONLoadDefaultLibrariesOnApplicationStartConfiguration;
import org.eclipse.syson.sysml.ASTTransformer;
import org.eclipse.syson.sysml.Package;
import org.eclipse.syson.sysml.SysmlToAst;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link OperationalConceptLabelServiceDelegate}, using the real textual-import pipeline to produce the
 * elements under test.
 *
 * @author DTPrendergast
 */
class OperationalConceptLabelServiceDelegateTests {

    private final OperationalConceptLabelServiceDelegate delegate = new OperationalConceptLabelServiceDelegate();

    @Test
    void handlesPackagesWithOperationalConceptMetadataApplied() {
        String snippet = """
                package Test {
                    import OperationalConceptLibrary::*;
                    package MyOperationalConcept {
                        @OperationalConcept;
                    }
                }
                """;
        Package operationalConceptPackage = this.parsePackage(snippet, "MyOperationalConcept");

        assertThat(this.delegate.canHandle(operationalConceptPackage)).isTrue();
        assertThat(this.delegate.getImagePaths(operationalConceptPackage)).containsExactly("/icons/full/obj16/UseCaseDefinition.svg");
        assertThat(this.delegate.getStyledLabel(operationalConceptPackage).toString()).isEqualTo("MyOperationalConcept");
    }

    @Test
    void declinesPackagesWithoutTheMetadata() {
        String snippet = """
                package Test {
                    package PlainPackage;
                }
                """;
        Package plainPackage = this.parsePackage(snippet, "PlainPackage");

        assertThat(this.delegate.canHandle(plainPackage)).isFalse();
    }

    @Test
    void declinesNonElementObjects() {
        assertThat(this.delegate.canHandle("not an element")).isFalse();
    }

    private Package parsePackage(String sysmlText, String packageName) {
        Resource resource = this.parseResource(sysmlText);
        return this.findPackage(resource, packageName);
    }

    private Resource parseResource(String sysmlText) {
        ResourceSet resourceSet = new SysONDefaultLibrariesConfiguration(new SysONLoadDefaultLibrariesOnApplicationStartConfiguration())
                .getLibrariesResourceSet();

        SysmlToAst sysmlToAst = new SysmlToAst(null);
        var astParsingResult = sysmlToAst.convert(new ByteArrayInputStream(sysmlText.getBytes()), "sysml");
        if (astParsingResult.ast().isEmpty()) {
            return fail("No AST produced, reports: " + astParsingResult.reports());
        }

        ASTTransformer transformer = new ASTTransformer();
        return transformer.convertResource(astParsingResult.ast().get(), resourceSet);
    }

    private Package findPackage(Resource resource, String packageName) {
        var iterator = resource.getAllContents();
        while (iterator.hasNext()) {
            var next = iterator.next();
            if (next instanceof Package pkg && packageName.equals(pkg.getDeclaredName())) {
                return pkg;
            }
        }
        return fail("No Package named " + packageName + " found in parsed resource");
    }
}
