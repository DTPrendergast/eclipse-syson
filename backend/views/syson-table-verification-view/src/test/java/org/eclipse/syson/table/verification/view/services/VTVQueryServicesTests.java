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
package org.eclipse.syson.table.verification.view.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.syson.application.configuration.SysONDefaultLibrariesConfiguration;
import org.eclipse.syson.application.configuration.SysONLoadDefaultLibrariesOnApplicationStartConfiguration;
import org.eclipse.syson.sysml.ASTTransformer;
import org.eclipse.syson.sysml.SysmlToAst;
import org.eclipse.syson.sysml.VerificationCaseUsage;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * Tests of {@link VTVQueryServices}, using the real textual-import pipeline (not hand-built EMF) to produce the
 * {@code VerificationCaseUsage} under test, so the shape being read is exactly what a user typing the same textual
 * SysML would produce.
 *
 * @author DTPrendergast
 */
class VTVQueryServicesTests {

    private static final String SNIPPET = """
            package Test {
                import VerificationCases::*;
                import VerificationProcedureLibrary::*;
                part def Foo {
                    verification vc1 : VerificationCase {
                        @VerificationMethod {
                            kind = VerificationMethodKind::demo;
                        }
                        @VerificationProcedure {
                            procedureId = "SYS-TP-014";
                        }
                    }
                }
            """;

    @Test
    void readsVerificationMethodAndProcedureId() {
        VerificationCaseUsage verificationCaseUsage = this.parse(SNIPPET);

        VTVQueryServices queryServices = new VTVQueryServices(JsonMapper.builder().build());

        assertThat(queryServices.getVerificationMethodLabel(verificationCaseUsage)).isEqualTo("Demonstration");
        assertThat(queryServices.getProcedureId(verificationCaseUsage)).isEqualTo("SYS-TP-014");
    }

    @Test
    void returnsEmptyStringsWhenNoMetadataApplied() {
        String snippetWithoutMetadata = """
                package Test {
                    import VerificationCases::*;
                    part def Foo {
                        verification vc1 : VerificationCase;
                    }
                }
                """;
        VerificationCaseUsage verificationCaseUsage = this.parse(snippetWithoutMetadata);

        VTVQueryServices queryServices = new VTVQueryServices(JsonMapper.builder().build());

        assertThat(queryServices.getVerificationMethodLabel(verificationCaseUsage)).isEmpty();
        assertThat(queryServices.getProcedureId(verificationCaseUsage)).isEmpty();
    }

    private VerificationCaseUsage parse(String sysmlText) {
        ResourceSet resourceSet = new SysONDefaultLibrariesConfiguration(new SysONLoadDefaultLibrariesOnApplicationStartConfiguration())
                .getLibrariesResourceSet();

        SysmlToAst sysmlToAst = new SysmlToAst(null);
        var astParsingResult = sysmlToAst.convert(new ByteArrayInputStream(sysmlText.getBytes()), "sysml");
        if (astParsingResult.ast().isEmpty()) {
            return fail("No AST produced, reports: " + astParsingResult.reports());
        }

        ASTTransformer transformer = new ASTTransformer();
        Resource resource = transformer.convertResource(astParsingResult.ast().get(), resourceSet);

        return this.findFirstVerificationCaseUsage(resource);
    }

    private VerificationCaseUsage findFirstVerificationCaseUsage(Resource resource) {
        var iterator = resource.getAllContents();
        while (iterator.hasNext()) {
            var next = iterator.next();
            if (next instanceof VerificationCaseUsage verificationCaseUsage) {
                return verificationCaseUsage;
            }
        }
        return fail("No VerificationCaseUsage found in parsed resource");
    }
}
