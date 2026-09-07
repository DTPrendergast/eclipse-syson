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
import org.eclipse.syson.sysml.services.SysideSysMLTextImporter;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * Round-trip tests of {@link RTVMutationServices#setThresholdOrObjective} and
 * {@link RTVQueryServices#getThresholdOrObjective}, including the {@code "objective"} reserved-keyword escaping
 * (see {@link RTVThresholdOrObjectiveMapping}'s Javadoc).
 *
 * @author DTPrendergast
 */
class RTVThresholdOrObjectiveTests {

    private final RTVQueryServices queryServices = new RTVQueryServices(JsonMapper.builder().build(), mock(IIdentityService.class));

    private final RTVMutationServices mutationServices = new RTVMutationServices(mock(IObjectSearchService.class), new SysideSysMLTextImporter(new SysmlToAst(null)));

    private final IEditingContext editingContext = mock(IEMFEditingContext.class);

    @Test
    void setsAndReadsBackThreshold() {
        RequirementUsage req1 = this.findFirst(this.parseResource(this.reqSnippet()), "req1");

        boolean success = this.mutationServices.setThresholdOrObjective(req1, "Threshold", this.editingContext);

        assertThat(success).isTrue();
        assertThat(this.queryServices.getThresholdOrObjective(req1)).isEqualTo("Threshold");
    }

    @Test
    void setsAndReadsBackObjectiveDespiteTheReservedKeyword() {
        RequirementUsage req1 = this.findFirst(this.parseResource(this.reqSnippet()), "req1");

        boolean success = this.mutationServices.setThresholdOrObjective(req1, "objective", this.editingContext);

        assertThat(success).isTrue();
        assertThat(this.queryServices.getThresholdOrObjective(req1)).isEqualTo("Objective");
    }

    @Test
    void rejectsAnUnknownValue() {
        RequirementUsage req1 = this.findFirst(this.parseResource(this.reqSnippet()), "req1");

        boolean success = this.mutationServices.setThresholdOrObjective(req1, "Neither", this.editingContext);

        assertThat(success).isFalse();
        assertThat(this.queryServices.getThresholdOrObjective(req1)).isEmpty();
    }

    @Test
    void blankValueClearsIt() {
        RequirementUsage req1 = this.findFirst(this.parseResource(this.reqSnippet()), "req1");
        this.mutationServices.setThresholdOrObjective(req1, "Threshold", this.editingContext);

        boolean success = this.mutationServices.setThresholdOrObjective(req1, "", this.editingContext);

        assertThat(success).isTrue();
        assertThat(this.queryServices.getThresholdOrObjective(req1)).isEmpty();
    }

    private String reqSnippet() {
        return """
                package Test {
                    requirement def R1;
                    requirement req1 : R1;
                }
                """;
    }

    private RequirementUsage findFirst(Resource resource, String declaredName) {
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
