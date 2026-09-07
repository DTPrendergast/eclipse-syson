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
package org.eclipse.syson.requirements.library.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.syson.application.configuration.SysONDefaultLibrariesConfiguration;
import org.eclipse.syson.application.configuration.SysONLoadDefaultLibrariesOnApplicationStartConfiguration;
import org.eclipse.syson.sysml.ASTTransformer;
import org.eclipse.syson.sysml.LiteralInteger;
import org.eclipse.syson.sysml.Package;
import org.eclipse.syson.sysml.SysmlToAst;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link RequirementIdSequenceQueryServices}, using the real textual-import pipeline to produce the
 * elements under test. Applied on a {@code Package}, not a requirement -- see
 * {@code RequirementAttributesLibrary::RequirementIdSequence}'s Javadoc.
 *
 * @author DTPrendergast
 */
class RequirementIdSequenceQueryServicesTests {

    private final RequirementIdSequenceQueryServices queryServices = new RequirementIdSequenceQueryServices();

    @Test
    void resolvesTheAppliedPrefixAndNextNumber() {
        String snippet = """
                package Test {
                    import RequirementAttributesLibrary::*;

                    @RequirementIdSequence {
                        prefix = "SYS-";
                        nextNumber = 5;
                    }
                }
                """;
        Package testPackage = this.findFirst(snippet, "Test");

        assertThat(this.queryServices.getPrefix(testPackage)).contains("SYS-");
        assertThat(this.queryServices.getNextNumberLiteral(testPackage)).isPresent();
        assertThat(this.queryServices.getNextNumberLiteral(testPackage).get().getValue()).isEqualTo(5);
    }

    @Test
    void incrementingTheReturnedLiteralInPlaceIsReflectedOnSubsequentReads() {
        String snippet = """
                package Test {
                    import RequirementAttributesLibrary::*;

                    @RequirementIdSequence {
                        prefix = "SYS-";
                        nextNumber = 5;
                    }
                }
                """;
        Package testPackage = this.findFirst(snippet, "Test");

        LiteralInteger nextNumber = this.queryServices.getNextNumberLiteral(testPackage).orElseThrow();
        nextNumber.setValue(nextNumber.getValue() + 1);

        assertThat(this.queryServices.getNextNumberLiteral(testPackage).get().getValue()).isEqualTo(6);
    }

    @Test
    void returnsEmptyWhenNoSequenceApplied() {
        String snippet = """
                package Test {
                }
                """;
        Package testPackage = this.findFirst(snippet, "Test");

        assertThat(this.queryServices.getPrefix(testPackage)).isEmpty();
        assertThat(this.queryServices.getNextNumberLiteral(testPackage)).isEmpty();
    }

    private Package findFirst(String sysmlText, String declaredName) {
        Resource resource = this.parseResource(sysmlText);
        var iterator = resource.getAllContents();
        while (iterator.hasNext()) {
            var next = iterator.next();
            if (next instanceof Package pkg && declaredName.equals(pkg.getDeclaredName())) {
                return pkg;
            }
        }
        return fail("No Package named " + declaredName + " found in parsed resource");
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
}
