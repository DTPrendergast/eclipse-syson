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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.syson.ai.services.generation.DerivedRequirementsTask;
import org.eclipse.syson.ai.services.generation.DraftUseCasesTask;
import org.eclipse.syson.ai.services.generation.TaskPrompts;
import org.eclipse.syson.ai.services.generation.GenerationRequest;
import org.eclipse.syson.ai.services.generation.GenerationResult;
import org.eclipse.syson.ai.services.generation.IGenerationClient;
import org.eclipse.syson.ai.services.proposal.AiProposal;
import org.eclipse.syson.ai.services.proposal.AiProposalItemStatus;
import org.eclipse.syson.ai.services.proposal.AiProposalStatus;
import org.eclipse.syson.ai.services.proposal.IAiProposalRepository;
import org.eclipse.syson.ai.services.scope.ModelScopePolicy;
import org.eclipse.syson.ai.services.scope.ModelScopeSelector;
import org.eclipse.syson.ai.services.scope.ModelScopeSerializer;
import org.eclipse.syson.ai.services.validation.GeneratedSysmlValidator;
import org.eclipse.syson.application.configuration.SysONDefaultLibrariesConfiguration;
import org.eclipse.syson.application.configuration.SysONLoadDefaultLibrariesOnApplicationStartConfiguration;
import org.eclipse.syson.sysml.ASTTransformer;
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.SysmlToAst;
import org.junit.jupiter.api.Test;

/**
 * Tests of the derived-requirements pipeline.
 *
 * <p>
 * Everything except the model call and the database is real: a real parsed model for the scope, the
 * real scope services, the real prompt, the real parser behind validation. Only
 * {@link IGenerationClient} is stubbed, since the point is to test what the pipeline does with a
 * response rather than to call the API.
 * </p>
 *
 * @author DTPrendergast
 */
class AiProposalPipelineTests {

    private static final String MODEL = """
            package PowerSystem {
                part def Battery;
                part battery : Battery;
                requirement <REQ1> enduranceRequirement {
                    doc /* The system shall operate for 8 hours. */
                    subject battery;
                }
            }
            """;

    private static final String TWO_GOOD_REQUIREMENTS = """
            requirement <DER1> cellCapacityRequirement {
                doc /* Each cell shall provide at least 3.2 Ah. */
            }
            requirement <DER2> thermalRequirement {
                doc /* The pack shall stay below 45 degrees C under load. */
            }
            """;

    private final IAiProposalRepository repository = mock(IAiProposalRepository.class);

    private final IGenerationClient generationClient = mock(IGenerationClient.class);

    private final GeneratedSysmlValidator validator = new GeneratedSysmlValidator(new SysmlToAst(null));

    private final ProposalApplyService applyService = mock(ProposalApplyService.class);

    private final DerivedRequirementsTask derivedRequirements = new DerivedRequirementsTask(new TaskPrompts(), this.validator, this.applyService);

    private final DraftUseCasesTask draftUseCases = new DraftUseCasesTask(new TaskPrompts(), this.validator, this.applyService);

    private final AiProposalPipeline pipeline = new AiProposalPipeline(this.generationClient, this.repository);

    @Test
    void storesOneReviewableItemPerCandidate() {
        this.stubGeneration(TWO_GOOD_REQUIREMENTS);
        when(this.repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var outcome = this.run();

        assertThat(outcome.hasSomethingToReview()).isTrue();
        assertThat(outcome.candidateCount()).isEqualTo(2);
        // The candidates come back with the outcome so the review UI needs no second round trip,
        // and each carries the exact fragment that will be applied if it is accepted.
        assertThat(outcome.items()).extracting(ProposedItem::label)
                .containsExactly("cellCapacityRequirement", "thermalRequirement");
        assertThat(outcome.items().get(0).sysmlFragment()).contains("3.2 Ah");
    }

    @Test
    void everyStoredItemStartsPendingAndTheProposalStartsAwaitingReview() {
        this.stubGeneration(TWO_GOOD_REQUIREMENTS);
        when(this.repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        this.run();

        var saved = this.captureSaved();
        // The review gate is a property of the data, not of the UI: nothing is applied, and the
        // pipeline has no code path into the model at all.
        assertThat(saved.getStatus()).isEqualTo(AiProposalStatus.PENDING_REVIEW);
        assertThat(saved.getItems()).allSatisfy(item -> assertThat(item.getStatus()).isEqualTo(AiProposalItemStatus.PENDING));
        assertThat(saved.getItems()).allSatisfy(item -> assertThat(item.getAppliedElementId()).isEmpty());
    }

    @Test
    void recordsProvenanceOnTheProposal() {
        this.stubGeneration(TWO_GOOD_REQUIREMENTS);
        when(this.repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        this.run();

        var saved = this.captureSaved();
        assertThat(saved.getPrompt()).isEqualTo("Derive cell-level requirements for the battery.");
        assertThat(saved.getModelId()).isEqualTo("claude-opus-5");
        assertThat(saved.getCreatedBy()).isEqualTo("ai-assistant");
        assertThat(saved.getTaskKind()).isEqualTo(DerivedRequirementsTask.KIND);
        assertThat(saved.getProjectId()).isEqualTo("project-1");
    }

    @Test
    void persistsNothingWhenTheResponseFailsValidation() {
        this.stubGeneration("Sure! Here are some requirements you might consider.");

        var outcome = this.run();

        assertThat(outcome.hasSomethingToReview()).isFalse();
        assertThat(outcome.problems()).isNotEmpty();
        // Storing candidates that failed validation would invite someone to apply them later.
        verify(this.repository, never()).save(any());
    }

    @Test
    void persistsNothingWhenTheModelDeclines() {
        when(this.generationClient.generate(any(GenerationRequest.class)))
                .thenReturn(new GenerationResult("", "claude-opus-5", GenerationResult.Outcome.REFUSED, "The model declined to answer this request."));

        var outcome = this.run();

        assertThat(outcome.hasSomethingToReview()).isFalse();
        assertThat(outcome.problems()).containsExactly("The model declined to answer this request.");
        verify(this.repository, never()).save(any());
    }

    @Test
    void persistsNothingWhenTheResponseWasTruncated() {
        when(this.generationClient.generate(any(GenerationRequest.class)))
                .thenReturn(new GenerationResult("requirement <DER1> halfWritten", "claude-opus-5", GenerationResult.Outcome.TRUNCATED, "hit the ceiling"));

        var outcome = this.run();

        // A fragment must not be parsed as if it were the whole answer.
        assertThat(outcome.hasSomethingToReview()).isFalse();
        verify(this.repository, never()).save(any());
    }

    @Test
    void treatsAnEmptyButValidResponseAsNothingToReviewRatherThanAFailure() {
        this.stubGeneration("");

        var outcome = this.run();

        assertThat(outcome.hasSomethingToReview()).isFalse();
        assertThat(outcome.problems()).containsExactly("The model did not propose anything from this context.");
        verify(this.repository, never()).save(any());
    }

    @Test
    void putsTheSelectedModelContextIntoThePrompt() {
        this.stubGeneration(TWO_GOOD_REQUIREMENTS);
        when(this.repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        this.run();

        var captor = org.mockito.ArgumentCaptor.forClass(GenerationRequest.class);
        verify(this.generationClient).generate(captor.capture());
        assertThat(captor.getValue().userPrompt()).contains("enduranceRequirement");
        assertThat(captor.getValue().userPrompt()).contains("Derive cell-level requirements for the battery.");
    }

    @Test
    void theTaskDecidesWhatKindOfCandidateIsAccepted() {
        // Use cases from the same pipeline: only the prompt and the expected kind differ, which is
        // the whole point of the task abstraction.
        this.stubGeneration("use case restorePower { doc /* Dispatch restores power to a feeder. */ }");
        when(this.repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Element seed = this.find("enduranceRequirement");
        var policy = ModelScopePolicy.defaultPolicy();
        var scope = new ModelScopeSerializer().serialize(new ModelScopeSelector().select(List.of(seed), policy), policy);

        var outcome = this.pipeline.run("project-1", scope, List.of("seed-1"), "Draft use cases.", "ai-assistant", this.draftUseCases);

        assertThat(outcome.hasSomethingToReview()).isTrue();
        assertThat(outcome.items()).extracting(ProposedItem::label).containsExactly("restorePower");
        assertThat(this.captureSaved().getTaskKind()).isEqualTo(DraftUseCasesTask.KIND);
    }

    @Test
    void aUseCaseIsRejectedWhenTheRequirementsTaskWasAskedFor() {
        this.stubGeneration("use case restorePower;");

        var outcome = this.run();

        // Same response, different task: the type check is what makes the tasks distinct rather
        // than the prompt wording, which a model is free to ignore.
        assertThat(outcome.hasSomethingToReview()).isFalse();
        assertThat(outcome.problems().get(0)).contains("Expected only RequirementUsage");
        verify(this.repository, never()).save(any());
    }

    @Test
    void refusesToAskWhenNothingIsInScope() {
        var empty = new org.eclipse.syson.ai.services.scope.SerializedModelScope("", 0, false, List.of());

        var outcome = this.pipeline.run("project-1", empty, List.of(), "Turn my notes into prose.", "ai-assistant", this.derivedRequirements);

        // No model call at all. A model told there is nothing to work from answers by saying so,
        // and that answer was being stored as a reviewable candidate - for the narrative task,
        // accepting it would have overwritten a real narrative with an apology.
        verify(this.generationClient, never()).generate(any(GenerationRequest.class));
        assertThat(outcome.hasSomethingToReview()).isFalse();
        assertThat(outcome.problems().get(0)).contains("Nothing was in scope");
        verify(this.repository, never()).save(any());
    }

    @Test
    void recordsWhichElementsItWasGeneratedFrom() {
        this.stubGeneration(TWO_GOOD_REQUIREMENTS);
        when(this.repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        this.run();

        // Resuming an unreviewed proposal has to be scoped to the element the reviewer has open:
        // candidates derived from one element applied to another are wrong for every task, and
        // destructive for the one that replaces a narrative.
        assertThat(this.captureSaved().getSeedElementIds()).containsExactly("seed-1");
    }

    private GenerationOutcome run() {
        // The scope is gathered the way the query handler gathers it, then handed over as text -
        // the pipeline never sees an EMF object.
        Element seed = this.find("enduranceRequirement");
        var policy = ModelScopePolicy.defaultPolicy();
        var scope = new ModelScopeSerializer().serialize(new ModelScopeSelector().select(List.of(seed), policy), policy);
        return this.pipeline.run("project-1", scope, List.of("seed-1"), "Derive cell-level requirements for the battery.", "ai-assistant", this.derivedRequirements);
    }

    private void stubGeneration(String responseText) {
        when(this.generationClient.generate(any(GenerationRequest.class)))
                .thenReturn(new GenerationResult(responseText, "claude-opus-5", GenerationResult.Outcome.ANSWERED, null));
    }

    private AiProposal captureSaved() {
        var captor = org.mockito.ArgumentCaptor.forClass(AiProposal.class);
        verify(this.repository).save(captor.capture());
        return captor.getValue();
    }

    private Element find(String declaredName) {
        var iterator = this.parseResource().getAllContents();
        while (iterator.hasNext()) {
            var next = iterator.next();
            if (next instanceof Element element && declaredName.equals(element.getDeclaredName())) {
                return element;
            }
        }
        return fail("No element named " + declaredName);
    }

    private Resource parseResource() {
        ResourceSet resourceSet = new SysONDefaultLibrariesConfiguration(new SysONLoadDefaultLibrariesOnApplicationStartConfiguration())
                .getLibrariesResourceSet();
        var astParsingResult = new SysmlToAst(null).convert(new ByteArrayInputStream(MODEL.getBytes()), "sysml");
        if (astParsingResult.ast().isEmpty()) {
            return fail("No AST produced");
        }
        return new ASTTransformer().convertResource(astParsingResult.ast().get(), resourceSet);
    }
}
