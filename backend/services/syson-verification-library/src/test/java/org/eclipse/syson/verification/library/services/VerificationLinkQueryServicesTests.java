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
package org.eclipse.syson.verification.library.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.syson.application.configuration.SysONDefaultLibrariesConfiguration;
import org.eclipse.syson.application.configuration.SysONLoadDefaultLibrariesOnApplicationStartConfiguration;
import org.eclipse.syson.sysml.ASTTransformer;
import org.eclipse.syson.sysml.RequirementUsage;
import org.eclipse.syson.sysml.SysmlToAst;
import org.eclipse.syson.sysml.VerificationCaseUsage;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link VerificationLinkQueryServices}, using the real textual-import pipeline to produce the elements
 * under test.
 *
 * @author DTPrendergast
 */
class VerificationLinkQueryServicesTests {

    private final VerificationLinkQueryServices queryServices = new VerificationLinkQueryServices();

    @Test
    void resolvesTheLinkedVerificationCase() {
        String snippet = """
                package Test {
                    import VerificationCases::*;
                    import VerificationProcedureLibrary::*;

                    requirement def R1;
                    requirement req1 : R1 {
                        @VerificationLink {
                            verificationCase = vc1;
                        }
                    }

                    verification vc1 : VerificationCase;
                }
                """;

        Resource resource = this.parseResource(snippet);
        RequirementUsage req1 = this.findFirst(resource, RequirementUsage.class, "req1");
        VerificationCaseUsage vc1 = this.findFirst(resource, VerificationCaseUsage.class, "vc1");

        assertThat(this.queryServices.getLinkedVerificationCase(req1)).contains(vc1);
    }

    @Test
    void returnsEmptyWhenNoLinkApplied() {
        String snippet = """
                package Test {
                    requirement def R1;
                    requirement req1 : R1;
                }
                """;

        Resource resource = this.parseResource(snippet);
        RequirementUsage req1 = this.findFirst(resource, RequirementUsage.class, "req1");

        assertThat(this.queryServices.getLinkedVerificationCase(req1)).isEmpty();
    }

    private <T extends org.eclipse.syson.sysml.Element> T findFirst(Resource resource, Class<T> type, String declaredName) {
        var iterator = resource.getAllContents();
        while (iterator.hasNext()) {
            var next = iterator.next();
            if (type.isInstance(next) && declaredName.equals(((org.eclipse.syson.sysml.Element) next).getDeclaredName())) {
                return type.cast(next);
            }
        }
        return fail("No " + type.getSimpleName() + " named " + declaredName + " found in parsed resource");
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
