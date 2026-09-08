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
package org.eclipse.syson.ai.services.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.sirius.components.core.api.IIdentityService;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.components.representations.Message;
import org.eclipse.syson.ai.services.proposal.AiProposal;
import org.eclipse.syson.ai.services.proposal.AiProposalItem;
import org.eclipse.syson.ai.services.proposal.AiProposalItemStatus;
import org.eclipse.syson.application.configuration.SysONDefaultLibrariesConfiguration;
import org.eclipse.syson.application.configuration.SysONLoadDefaultLibrariesOnApplicationStartConfiguration;
import org.eclipse.syson.sysml.ASTTransformer;
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.LiteralString;
import org.eclipse.syson.sysml.MetadataUsage;
import org.eclipse.syson.sysml.Package;
import org.eclipse.syson.sysml.RequirementUsage;
import org.eclipse.syson.sysml.SysmlToAst;
import org.eclipse.syson.sysml.services.SysideSysMLTextImporter;
import org.junit.jupiter.api.Test;

/**
 * Tests that applying an accepted candidate creates the element and stamps it with where it came
 * from, in one import.
 *
 * <p>
 * The importer here is the real one. The whole question this slice has to answer is whether a
 * provenance annotation written as text actually attaches to the element the parser creates, and
 * only the real parser can answer that - especially in a grammar that has already been shown to
 * drop things silently.
 * </p>
 *
 * @author DTPrendergast
 */
class ProposalApplyServiceTests {

    private static final String MODEL = """
            package PowerSystem {
                part battery;
            }
            """;

    private final IIdentityService identityService = mock(IIdentityService.class);

    private final ProposalApplyService applyService = new ProposalApplyService(
            new SysideSysMLTextImporter(new SysmlToAst(null)), this.identityService);

    @Test
    void createsTheElementAndMarksTheItemApplied() {
        when(this.identityService.getId(org.mockito.ArgumentMatchers.any())).thenReturn("element-id-1");
        var parent = this.parentPackage();
        var proposal = this.proposalWith("requirement <DER1> cellCapacityRequirement { doc /* Each cell shall provide 3.2 Ah. */ }");
        var messages = new ArrayList<Message>();

        boolean applied = this.applyService.apply(mock(IEMFEditingContext.class), parent, proposal, proposal.getItems().get(0), messages);

        assertThat(applied).isTrue();
        assertThat(proposal.getItems().get(0).getStatus()).isEqualTo(AiProposalItemStatus.APPLIED);
        assertThat(proposal.getItems().get(0).getAppliedElementId()).contains("element-id-1");
        assertThat(this.requirementsUnder(parent)).extracting(Element::getDeclaredName).contains("cellCapacityRequirement");
    }

    @Test
    void stampsProvenanceOntoTheCreatedElementInTheSameImport() {
        when(this.identityService.getId(org.mockito.ArgumentMatchers.any())).thenReturn("element-id-1");
        var parent = this.parentPackage();
        var proposal = this.proposalWith("requirement <DER1> cellCapacityRequirement;");

        this.applyService.apply(mock(IEMFEditingContext.class), parent, proposal, proposal.getItems().get(0), new ArrayList<>());

        var requirement = this.requirementsUnder(parent).stream()
                .filter(element -> "cellCapacityRequirement".equals(element.getDeclaredName()))
                .findFirst()
                .orElseGet(() -> fail("the requirement was not created"));

        // The annotation has to have actually attached. A metadata definition that silently fails
        // to apply would leave generated content indistinguishable from hand-authored content,
        // which is the exact failure this whole library exists to prevent.
        List<MetadataUsage> metadata = requirement.getOwnedElement().stream()
                .filter(MetadataUsage.class::isInstance)
                .map(MetadataUsage.class::cast)
                .toList();
        assertThat(metadata).as("no provenance metadata attached to the generated element").isNotEmpty();
    }

    @Test
    void theProvenanceValuesCanBeReadBackOffTheElement() {
        when(this.identityService.getId(org.mockito.ArgumentMatchers.any())).thenReturn("element-id-1");
        var parent = this.parentPackage();
        var proposal = this.proposalWith("requirement <DER1> cellCapacityRequirement;");

        this.applyService.apply(mock(IEMFEditingContext.class), parent, proposal, proposal.getItems().get(0), new ArrayList<>());

        var requirement = this.requirementsUnder(parent).stream()
                .filter(element -> "cellCapacityRequirement".equals(element.getDeclaredName()))
                .findFirst()
                .orElseGet(() -> fail("the requirement was not created"));

        // An attached annotation carrying nothing would be provenance in name only, so the values
        // themselves are what this asserts.
        var values = this.literalsUnder(requirement);
        assertThat(values).contains("claude-opus-5");
        assertThat(values).contains(proposal.getId().toString());
    }

    private List<String> literalsUnder(Element element) {
        var values = new ArrayList<String>();
        var iterator = element.eAllContents();
        while (iterator.hasNext()) {
            if (iterator.next() instanceof LiteralString literal) {
                values.add(literal.getValue());
            }
        }
        return values;
    }

    private List<Element> requirementsUnder(Element parent) {
        return parent.getOwnedElement().stream()
                .filter(RequirementUsage.class::isInstance)
                .map(Element.class::cast)
                .toList();
    }

    private AiProposal proposalWith(String fragment) {
        return new AiProposal("project-1", DerivedRequirementsPipeline.TASK_KIND, "derive things", "claude-opus-5", "dtprendergast",
                List.of(new AiProposalItem("DER1", fragment)));
    }

    private Element parentPackage() {
        ResourceSet resourceSet = new SysONDefaultLibrariesConfiguration(new SysONLoadDefaultLibrariesOnApplicationStartConfiguration())
                .getLibrariesResourceSet();
        var parse = new SysmlToAst(null).convert(new ByteArrayInputStream(MODEL.getBytes()), "sysml");
        if (parse.ast().isEmpty()) {
            return fail("no AST");
        }
        Resource resource = new ASTTransformer().convertResource(parse.ast().get(), resourceSet);
        Element found = null;
        var iterator = resource.getAllContents();
        while (iterator.hasNext() && found == null) {
            var next = iterator.next();
            if (next instanceof Package packageElement && "PowerSystem".equals(packageElement.getDeclaredName())) {
                found = packageElement;
            }
        }
        if (found == null) {
            found = fail("no PowerSystem package");
        }
        return found;
    }
}
