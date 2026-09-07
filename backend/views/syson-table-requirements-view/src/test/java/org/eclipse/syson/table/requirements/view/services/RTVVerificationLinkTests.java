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
package org.eclipse.syson.table.requirements.view.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IIdentityService;
import org.eclipse.sirius.components.core.api.IObjectSearchService;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.syson.application.configuration.SysONDefaultLibrariesConfiguration;
import org.eclipse.syson.application.configuration.SysONLoadDefaultLibrariesOnApplicationStartConfiguration;
import org.eclipse.syson.sysml.ASTTransformer;
import org.eclipse.syson.sysml.RequirementUsage;
import org.eclipse.syson.sysml.SysmlToAst;
import org.eclipse.syson.sysml.VerificationCaseUsage;
import org.eclipse.syson.sysml.services.SysideSysMLTextImporter;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * Round-trip tests of {@link RTVMutationServices#setVerificationLink} and
 * {@link RTVQueryServices#getVerificationProcedureId}: sets the link via the real textual-import pipeline
 * (exactly as {@code RTVTableDescriptionProvider} wires the cell), then reads it back, exercising both classes
 * together rather than each in isolation.
 *
 * @author DTPrendergast
 */
class RTVVerificationLinkTests {

    private final RTVQueryServices queryServices = new RTVQueryServices(JsonMapper.builder().build(), mock(IIdentityService.class));

    private final RTVMutationServices mutationServices = new RTVMutationServices(mock(IObjectSearchService.class), new SysideSysMLTextImporter(new SysmlToAst(null)));

    private final IEditingContext editingContext = mock(IEMFEditingContext.class);

    @Test
    void linksToAMatchingVerificationCaseAndReadsItsProcedureId() {
        String snippet = """
                package Test {
                    import VerificationCases::*;
                    import VerificationProcedureLibrary::*;

                    requirement def R1;
                    requirement req1 : R1;

                    verification vc1 : VerificationCase {
                        @VerificationProcedure {
                            procedureId = "SYS-TP-014";
                        }
                    }
                }
                """;
        RequirementUsage req1 = this.findFirst(snippet, RequirementUsage.class, "req1");

        boolean success = this.mutationServices.setVerificationLink(req1, "vc1", this.editingContext);

        assertThat(success).isTrue();
        assertThat(this.queryServices.getVerificationProcedureId(req1)).isEqualTo("SYS-TP-014");
    }

    @Test
    void rejectsAnUnknownVerificationCaseName() {
        String snippet = """
                package Test {
                    requirement def R1;
                    requirement req1 : R1;
                }
                """;
        RequirementUsage req1 = this.findFirst(snippet, RequirementUsage.class, "req1");

        boolean success = this.mutationServices.setVerificationLink(req1, "doesNotExist", this.editingContext);

        assertThat(success).isFalse();
        assertThat(this.queryServices.getVerificationProcedureId(req1)).isEmpty();
    }

    @Test
    void rejectsAnAmbiguousVerificationCaseName() {
        String snippet = """
                package Test {
                    import VerificationCases::*;

                    requirement def R1;
                    requirement req1 : R1;

                    part def Foo {
                        verification vc1 : VerificationCase;
                    }
                    part def Bar {
                        verification vc1 : VerificationCase;
                    }
                }
                """;
        RequirementUsage req1 = this.findFirst(snippet, RequirementUsage.class, "req1");

        boolean success = this.mutationServices.setVerificationLink(req1, "vc1", this.editingContext);

        assertThat(success).isFalse();
    }

    @Test
    void blankValueClearsAnExistingLink() {
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
        RequirementUsage req1 = this.findFirst(snippet, RequirementUsage.class, "req1");

        boolean success = this.mutationServices.setVerificationLink(req1, "", this.editingContext);

        assertThat(success).isTrue();
        assertThat(this.queryServices.getVerificationProcedureId(req1)).isEmpty();
    }

    private <T extends org.eclipse.syson.sysml.Element> T findFirst(String sysmlText, Class<T> type, String declaredName) {
        Resource resource = this.parseResource(sysmlText);
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
