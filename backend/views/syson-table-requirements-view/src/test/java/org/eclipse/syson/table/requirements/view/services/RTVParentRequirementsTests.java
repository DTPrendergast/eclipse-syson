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
 * Round-trip tests of {@link RTVMutationServices#setParentRequirements} and
 * {@link RTVQueryServices#getParentRequirementIds}.
 *
 * <p>
 * {@code reqId} is set directly via {@link RequirementUsage#setReqId} after parsing, not inline in the textual
 * fixture: it is a genuine {@code EAttribute}, not a redefineable feature, so {@code reqId = "X";} inside a
 * requirement body silently does not set it (confirmed via a scratch test) -- the real ReqId column cell edits it
 * the same way, via a plain EMF {@code SetValue}, not a textual reimport.
 * </p>
 *
 * @author DTPrendergast
 */
class RTVParentRequirementsTests {

    private final RTVQueryServices queryServices = new RTVQueryServices(JsonMapper.builder().build(), mock(IIdentityService.class));

    private final RTVMutationServices mutationServices = new RTVMutationServices(mock(IObjectSearchService.class), new SysideSysMLTextImporter(new SysmlToAst(null)));

    private final IEditingContext editingContext = mock(IEMFEditingContext.class);

    @Test
    void linksToASingleParentByReqId() {
        String snippet = """
                package Test {
                    requirement def R1;
                    requirement req1 : R1;
                    requirement req2 : R1;
                }
                """;
        Resource resource = this.parseResource(snippet);
        RequirementUsage req1 = this.findFirst(resource, "req1");
        RequirementUsage req2 = this.findFirst(resource, "req2");
        req2.setReqId("PARENT-1");

        boolean success = this.mutationServices.setParentRequirements(req1, "PARENT-1", this.editingContext);

        assertThat(success).isTrue();
        assertThat(this.queryServices.getParentRequirementIds(req1)).isEqualTo("PARENT-1");
    }

    @Test
    void linksToMultipleParentsByReqId() {
        String snippet = """
                package Test {
                    requirement def R1;
                    requirement req1 : R1;
                    requirement req2 : R1;
                    requirement req3 : R1;
                }
                """;
        Resource resource = this.parseResource(snippet);
        RequirementUsage req1 = this.findFirst(resource, "req1");
        this.findFirst(resource, "req2").setReqId("PARENT-1");
        this.findFirst(resource, "req3").setReqId("PARENT-2");

        boolean success = this.mutationServices.setParentRequirements(req1, "PARENT-1, PARENT-2", this.editingContext);

        assertThat(success).isTrue();
        assertThat(this.queryServices.getParentRequirementIds(req1)).contains("PARENT-1").contains("PARENT-2");
    }

    @Test
    void rejectsAnUnknownReqId() {
        String snippet = """
                package Test {
                    requirement def R1;
                    requirement req1 : R1;
                }
                """;
        RequirementUsage req1 = this.findFirst(this.parseResource(snippet), "req1");

        boolean success = this.mutationServices.setParentRequirements(req1, "DOES-NOT-EXIST", this.editingContext);

        assertThat(success).isFalse();
        assertThat(this.queryServices.getParentRequirementIds(req1)).isEmpty();
    }

    @Test
    void blankValueClearsExistingLinks() {
        String snippet = """
                package Test {
                    requirement def R1;
                    requirement req1 : R1 {
                        dependency from Test::req1 to Test::req2;
                    }
                    requirement req2 : R1;
                }
                """;
        Resource resource = this.parseResource(snippet);
        RequirementUsage req1 = this.findFirst(resource, "req1");
        this.findFirst(resource, "req2").setReqId("PARENT-1");
        assertThat(this.queryServices.getParentRequirementIds(req1)).isEqualTo("PARENT-1");

        boolean success = this.mutationServices.setParentRequirements(req1, "", this.editingContext);

        assertThat(success).isTrue();
        assertThat(this.queryServices.getParentRequirementIds(req1)).isEmpty();
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
