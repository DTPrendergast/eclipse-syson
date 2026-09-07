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
import java.util.List;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IObjectSearchService;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.syson.application.configuration.SysONDefaultLibrariesConfiguration;
import org.eclipse.syson.application.configuration.SysONLoadDefaultLibrariesOnApplicationStartConfiguration;
import org.eclipse.syson.sysml.ASTTransformer;
import org.eclipse.syson.sysml.MembershipExpose;
import org.eclipse.syson.sysml.RequirementUsage;
import org.eclipse.syson.sysml.SysmlToAst;
import org.eclipse.syson.sysml.ViewUsage;
import org.eclipse.syson.sysml.services.SysideSysMLTextImporter;
import org.junit.jupiter.api.Test;

/**
 * Tests of the auto-assigned {@code reqId} in {@link RTVMutationServices#createRequirement}, backed by the
 * {@code RequirementIdSequence} metadata (see that metadata's Javadoc in
 * {@code syson-requirements-library}). Uses a real {@code ViewUsage}, parsed from the same textual shape as
 * {@code ImportExportTests.checkEscapedKeywordName}, since {@code createRequirement} needs an actual owning
 * namespace to create the requirement in and to carry the sequence metadata.
 *
 * @author DTPrendergast
 */
class RTVAutoRequirementIdTests {

    private final RTVMutationServices mutationServices = new RTVMutationServices(mock(IObjectSearchService.class), new SysideSysMLTextImporter(new SysmlToAst(null)));

    private final IEditingContext editingContext = mock(IEMFEditingContext.class);

    @Test
    void assignsSequentialIdsWithTheDefaultPrefix() {
        ViewUsage viewUsage = this.findFirst(this.parseResource(this.viewSnippet()), "view1");

        boolean firstSuccess = this.mutationServices.createRequirement(viewUsage, this.editingContext);
        boolean secondSuccess = this.mutationServices.createRequirement(viewUsage, this.editingContext);

        assertThat(firstSuccess).isTrue();
        assertThat(secondSuccess).isTrue();
        assertThat(this.exposedRequirementReqIds(viewUsage)).containsExactly("REQ-001", "REQ-002");
    }

    @Test
    void usesAPreExistingCustomPrefixAndStartingNumber() {
        ViewUsage viewUsage = this.findFirst(this.parseResource(this.viewSnippetWithCustomSequence()), "view1");

        boolean success = this.mutationServices.createRequirement(viewUsage, this.editingContext);

        assertThat(success).isTrue();
        assertThat(this.exposedRequirementReqIds(viewUsage)).containsExactly("SYS-010");
    }

    @Test
    void neverReusesANumberEvenIfTheHighestNumberedRequirementIsDeleted() {
        ViewUsage viewUsage = this.findFirst(this.parseResource(this.viewSnippet()), "view1");
        this.mutationServices.createRequirement(viewUsage, this.editingContext);
        RequirementUsage first = this.exposedRequirements(viewUsage).get(0);
        assertThat(first.getReqId()).isEqualTo("REQ-001");

        // Simulate deleting the first (and, so far, only) requirement: remove its MembershipExpose from the
        // ViewUsage, the same effect as RTVMutationServices.removeFromExposedElements / a full model deletion.
        viewUsage.getOwnedRelationship().stream()
                .filter(MembershipExpose.class::isInstance)
                .map(MembershipExpose.class::cast)
                .filter(membershipExpose -> membershipExpose.getImportedElement() == first)
                .findFirst()
                .ifPresent(membershipExpose -> viewUsage.getOwnedRelationship().remove(membershipExpose));

        boolean success = this.mutationServices.createRequirement(viewUsage, this.editingContext);

        assertThat(success).isTrue();
        // The sequence's own next number, not a scan of what currently exists: REQ-001 having been deleted must
        // not free it up for reuse.
        assertThat(this.exposedRequirementReqIds(viewUsage)).containsExactly("REQ-002");
    }

    @Test
    void manualOverrideOfTheReqIdColumnStillWorksAfterAutoAssignment() {
        ViewUsage viewUsage = this.findFirst(this.parseResource(this.viewSnippet()), "view1");
        this.mutationServices.createRequirement(viewUsage, this.editingContext);
        RequirementUsage created = this.exposedRequirements(viewUsage).get(0);
        assertThat(created.getReqId()).isEqualTo("REQ-001");

        // Same mechanism the real ReqId cell uses (a plain EMF SetValue on the "reqId" feature, not a textual
        // reimport) -- untouched by this task, exercised here to confirm the two don't interfere.
        created.setReqId("CUSTOM-OVERRIDE");

        assertThat(created.getReqId()).isEqualTo("CUSTOM-OVERRIDE");
    }

    private List<String> exposedRequirementReqIds(ViewUsage viewUsage) {
        return this.exposedRequirements(viewUsage).stream().map(RequirementUsage::getReqId).toList();
    }

    private List<RequirementUsage> exposedRequirements(ViewUsage viewUsage) {
        return viewUsage.getExposedElement().stream()
                .filter(RequirementUsage.class::isInstance)
                .map(RequirementUsage.class::cast)
                .toList();
    }

    private String viewSnippet() {
        return """
                package Test {
                    view view1 : StandardViewDefinitions::GeneralView;
                }
                """;
    }

    private String viewSnippetWithCustomSequence() {
        return """
                package Test {
                    import RequirementAttributesLibrary::*;

                    @RequirementIdSequence {
                        prefix = "SYS-";
                        nextNumber = 10;
                    }

                    view view1 : StandardViewDefinitions::GeneralView;
                }
                """;
    }

    private ViewUsage findFirst(Resource resource, String declaredName) {
        var iterator = resource.getAllContents();
        while (iterator.hasNext()) {
            var next = iterator.next();
            if (next instanceof ViewUsage viewUsage && declaredName.equals(viewUsage.getDeclaredName())) {
                return viewUsage;
            }
        }
        return fail("No ViewUsage named " + declaredName + " found in parsed resource");
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
