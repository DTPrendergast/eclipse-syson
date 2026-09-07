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
import org.eclipse.syson.sysml.RequirementUsage;
import org.eclipse.syson.sysml.SysmlToAst;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link RequirementAttributesQueryServices}, using the real textual-import pipeline to produce the
 * elements under test.
 *
 * @author DTPrendergast
 */
class RequirementAttributesQueryServicesTests {

    private final RequirementAttributesQueryServices queryServices = new RequirementAttributesQueryServices();

    @Test
    void resolvesTheAppliedThresholdOrObjectiveKind() {
        String snippet = """
                package Test {
                    import RequirementAttributesLibrary::*;

                    requirement def R1;
                    requirement req1 : R1 {
                        @ThresholdOrObjective {
                            // "objective" is a reserved keyword in this grammar; escaped with single quotes,
                            // same as in the library source (see RequirementAttributes.sysml).
                            kind = ThresholdOrObjectiveKind::'objective';
                        }
                    }
                }
                """;

        RequirementUsage req1 = this.findFirst(snippet, "req1");

        assertThat(this.queryServices.getThresholdOrObjective(req1)).isEqualTo("objective");
    }

    @Test
    void resolvesTheAppliedCategory() {
        String snippet = """
                package Test {
                    import RequirementAttributesLibrary::*;

                    requirement def R1;
                    requirement req1 : R1 {
                        @RequirementCategory {
                            category = "Safety";
                        }
                    }
                }
                """;

        RequirementUsage req1 = this.findFirst(snippet, "req1");

        assertThat(this.queryServices.getCategory(req1)).isEqualTo("Safety");
    }

    @Test
    void returnsEmptyStringsWhenNoMetadataApplied() {
        String snippet = """
                package Test {
                    requirement def R1;
                    requirement req1 : R1;
                }
                """;

        RequirementUsage req1 = this.findFirst(snippet, "req1");

        assertThat(this.queryServices.getThresholdOrObjective(req1)).isEmpty();
        assertThat(this.queryServices.getCategory(req1)).isEmpty();
    }

    private RequirementUsage findFirst(String sysmlText, String declaredName) {
        Resource resource = this.parseResource(sysmlText);
        var iterator = resource.getAllContents();
        while (iterator.hasNext()) {
            var next = iterator.next();
            if (next instanceof RequirementUsage requirementUsage && declaredName.equals(requirementUsage.getDeclaredName())) {
                return requirementUsage;
            }
        }
        return fail("No RequirementUsage named " + declaredName + " found in parsed resource");
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
