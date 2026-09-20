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
package org.eclipse.syson.ai.services.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.components.representations.Message;
import org.eclipse.syson.ai.services.proposal.AiProposal;
import org.eclipse.syson.ai.services.proposal.AiProposalItem;
import org.eclipse.syson.ai.services.proposal.AiProposalItemStatus;
import org.eclipse.syson.application.configuration.SysONDefaultLibrariesConfiguration;
import org.eclipse.syson.application.configuration.SysONLoadDefaultLibrariesOnApplicationStartConfiguration;
import org.eclipse.syson.operational.concept.library.OperationalConceptNarrativeService;
import org.eclipse.syson.sysml.ASTTransformer;
import org.eclipse.syson.sysml.Documentation;
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.SysmlToAst;
import org.junit.jupiter.api.Test;

/**
 * Tests of the narrative drafting task - the one whose answer is prose rather than model elements.
 *
 * @author DTPrendergast
 */
class DraftNarrativeTaskTests {

    private static final String CONCEPT = """
            package DispatchOperations {
                doc /* Everything to do with outage dispatch. */
                @OperationalConceptLibrary::OperationalConcept;
                rep conopsNarrative language "markdown" /* - dispatcher triages
                                                           - crew restores power */
            }
            """;

    private final OperationalConceptNarrativeService narrativeService = new OperationalConceptNarrativeService();

    private final DraftNarrativeTask task = new DraftNarrativeTask(new TaskPrompts(), this.narrativeService);

    @Test
    void acceptsProseWithoutParsingIt() {
        var result = this.task.validate("The dispatcher triages the outage, then the crew restores power.");

        // Deliberately no parse: prose parses cleanly as SysML, so a parse would prove nothing and
        // a type check would reject every valid answer.
        assertThat(result.isValid()).isTrue();
        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().get(0).sysmlFragment()).startsWith("The dispatcher triages");
    }

    @Test
    void treatsAnEmptyResponseAsNothingToReview() {
        assertThat(this.task.validate("   ").candidates()).isEmpty();
        assertThat(this.task.validate(null).candidates()).isEmpty();
    }

    @Test
    void stripsCodeFencesAndSaysSo() {
        var result = this.task.validate("```markdown\n# Narrative\n\nThe dispatcher triages.\n```");

        assertThat(result.candidates().get(0).sysmlFragment()).startsWith("# Narrative");
        assertThat(result.candidates().get(0).sysmlFragment()).doesNotContain("```");
        assertThat(result.fencesStripped()).isTrue();
    }

    @Test
    void applyingReplacesTheNarrativeRatherThanAddingToIt() {
        Element concept = this.findConcept();
        var proposal = this.proposalWith("The dispatcher triages the outage; the crew then restores power.");
        var messages = new ArrayList<Message>();

        boolean applied = this.task.apply(mock(IEMFEditingContext.class), concept, proposal, proposal.getItems().get(0), messages);

        assertThat(applied).isTrue();
        assertThat(this.narrativeService.getNarrative(concept)).isEqualTo("The dispatcher triages the outage; the crew then restores power.");

        // Replacing, not appending: two narratives would both be valid SysML and would export as
        // two reps, so nothing would tell you which one was the narrative.
        long narrativeCount = concept.getOwnedElement().stream()
                .filter(org.eclipse.syson.sysml.TextualRepresentation.class::isInstance)
                .count();
        assertThat(narrativeCount).isEqualTo(1);
    }

    @Test
    void applyingLeavesThePackageDocumentationAlone() {
        Element concept = this.findConcept();
        var proposal = this.proposalWith("rewritten prose");

        this.task.apply(mock(IEMFEditingContext.class), concept, proposal, proposal.getItems().get(0), new ArrayList<>());

        String packageDoc = concept.getOwnedElement().stream()
                .filter(Documentation.class::isInstance)
                .map(Documentation.class::cast)
                .map(Documentation::getBody)
                .findFirst()
                .orElse("");
        assertThat(packageDoc).isEqualTo("Everything to do with outage dispatch.");
    }

    @Test
    void marksTheItemAppliedSoAResumedReviewDoesNotOfferItAgain() {
        Element concept = this.findConcept();
        var proposal = this.proposalWith("rewritten prose");

        this.task.apply(mock(IEMFEditingContext.class), concept, proposal, proposal.getItems().get(0), new ArrayList<>());

        assertThat(proposal.getItems().get(0).getStatus()).isEqualTo(AiProposalItemStatus.APPLIED);
    }

    @Test
    void refusesAnElementThatIsNotAnOperationalConcept() {
        Element plain = this.findPlainPackage();
        var proposal = this.proposalWith("prose with nowhere to live");
        var messages = new ArrayList<Message>();

        boolean applied = this.task.apply(mock(IEMFEditingContext.class), plain, proposal, proposal.getItems().get(0), messages);

        assertThat(applied).isFalse();
        assertThat(messages).isNotEmpty();
        assertThat(messages.get(0).body()).contains("not an operational concept");
    }

    @Test
    void theExistingNotesReachTheModelInThePrompt() {
        var scope = new org.eclipse.syson.ai.services.scope.SerializedModelScope(
                "doc /* - dispatcher triages\n- crew restores power */", 1, false, List.of());

        var request = this.task.prompt(scope, "Turn my notes into prose.");

        assertThat(request.userPrompt()).contains("dispatcher triages");
        assertThat(request.systemPrompt()).contains("Carry over every point");
        // The first draft of this prompt produced one sentence per bullet and nothing else, because
        // it said "rewriting, not deciding what matters" and offered leaving bullets as an out.
        assertThat(request.systemPrompt()).contains("Group related points into paragraphs");
        assertThat(request.systemPrompt()).contains("Follow any length or structure the engineer asks for");
        // The heading rule earned its place: the first narrative written in this project used
        // "#Introduction" with no space, which is not a heading in any Markdown dialect.
        assertThat(request.systemPrompt()).contains("needs a space after the hashes");
    }

    private AiProposal proposalWith(String prose) {
        return new AiProposal("project-1", DraftNarrativeTask.KIND, "turn notes into prose", "claude-opus-5", "ai-assistant", List.of("seed-1"),
                List.of(new AiProposalItem("Rewritten narrative", prose)));
    }

    private Element findConcept() {
        return this.findPackage(CONCEPT, "DispatchOperations");
    }

    private Element findPlainPackage() {
        return this.findPackage("package Plain { part x; }", "Plain");
    }

    private Element findPackage(String source, String name) {
        Element found = null;
        var iterator = this.parse(source).getAllContents();
        while (iterator.hasNext() && found == null) {
            var next = iterator.next();
            if (next instanceof org.eclipse.syson.sysml.Package packageElement && name.equals(packageElement.getDeclaredName())) {
                found = packageElement;
            }
        }
        if (found == null) {
            found = fail("No package named " + name);
        }
        return found;
    }

    private Resource parse(String source) {
        var resourceSet = new SysONDefaultLibrariesConfiguration(new SysONLoadDefaultLibrariesOnApplicationStartConfiguration()).getLibrariesResourceSet();
        var parsed = new SysmlToAst(null).convert(new ByteArrayInputStream(source.getBytes()), "sysml");
        if (parsed.ast().isEmpty()) {
            return fail("No AST: " + parsed.reports());
        }
        return new ASTTransformer().convertResource(parsed.ast().get(), resourceSet);
    }
}
