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
 * Round-trip tests of {@link RTVMutationServices#setCategory} and {@link RTVQueryServices#getCategory}.
 *
 * @author DTPrendergast
 */
class RTVCategoryTests {

    private final RTVQueryServices queryServices = new RTVQueryServices(JsonMapper.builder().build(), mock(IIdentityService.class));

    private final RTVMutationServices mutationServices = new RTVMutationServices(mock(IObjectSearchService.class), new SysideSysMLTextImporter(new SysmlToAst(null)));

    private final IEditingContext editingContext = mock(IEMFEditingContext.class);

    @Test
    void setsAndReadsBackACategory() {
        RequirementUsage req1 = this.findFirst(this.parseResource(this.reqSnippet()), "req1");

        boolean success = this.mutationServices.setCategory(req1, "Safety", this.editingContext);

        assertThat(success).isTrue();
        assertThat(this.queryServices.getCategory(req1)).isEqualTo("Safety");
    }

    @Test
    void roundTripsABackslashWithoutAnyEscaping() {
        RequirementUsage req1 = this.findFirst(this.parseResource(this.reqSnippet()), "req1");

        boolean success = this.mutationServices.setCategory(req1, "Safety \\ path", this.editingContext);

        assertThat(success).isTrue();
        assertThat(this.queryServices.getCategory(req1)).isEqualTo("Safety \\ path");
    }

    @Test
    void rejectsAValueContainingAQuote() {
        RequirementUsage req1 = this.findFirst(this.parseResource(this.reqSnippet()), "req1");

        boolean success = this.mutationServices.setCategory(req1, "Safety \"critical\"", this.editingContext);

        // This grammar's string literals have no working escape for an embedded quote (confirmed via a scratch
        // test: both backslash-escaping and doubling silently truncate the value instead of erroring), so this
        // is rejected outright rather than risk silent corruption -- see setCategory's Javadoc.
        assertThat(success).isFalse();
        assertThat(this.queryServices.getCategory(req1)).isEmpty();
    }

    @Test
    void blankValueClearsIt() {
        RequirementUsage req1 = this.findFirst(this.parseResource(this.reqSnippet()), "req1");
        this.mutationServices.setCategory(req1, "Safety", this.editingContext);

        boolean success = this.mutationServices.setCategory(req1, "", this.editingContext);

        assertThat(success).isTrue();
        assertThat(this.queryServices.getCategory(req1)).isEmpty();
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
