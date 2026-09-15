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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.syson.ai.services.generation.IAiTask;
import org.eclipse.syson.ai.services.generation.GenerationResult;
import org.eclipse.syson.ai.services.generation.IGenerationClient;
import org.eclipse.syson.ai.services.proposal.AiProposal;
import org.eclipse.syson.ai.services.proposal.AiProposalItem;
import org.eclipse.syson.ai.services.proposal.IAiProposalRepository;
import org.eclipse.syson.ai.services.scope.SerializedModelScope;
import org.eclipse.syson.ai.services.validation.ValidationResult;
import org.springframework.stereotype.Service;

/**
 * Runs one assistant task: prompt, generate, validate, persist.
 *
 * <p>
 * <b>Nothing here touches the model - it cannot.</b> This takes an already-serialized scope rather
 * than model elements, so there is no EMF object in reach and no way for a long-running model call
 * to end up on the editing context's thread. That is the two-phase design from plan.md made
 * structural rather than merely intended: the platform dispatches every mutation through one
 * single-threaded executor per project, so an LLM call inside a mutation handler would block every
 * edit for every user for the length of the call. Gathering the scope happens on that thread and
 * finishes quickly; this runs off it; applying an accepted candidate is a separate fast mutation.
 * </p>
 *
 * <p>
 * It is also where the human-review gate stops being a UI convention and becomes a property of the
 * data: this writes candidates with per-item {@code PENDING} status and nothing else. There is no
 * code path from here into the model at all.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
public class AiProposalPipeline {

    private final IGenerationClient generationClient;

    private final IAiProposalRepository proposalRepository;

    public AiProposalPipeline(IGenerationClient generationClient, IAiProposalRepository proposalRepository) {
        this.generationClient = Objects.requireNonNull(generationClient);
        this.proposalRepository = Objects.requireNonNull(proposalRepository);
    }

    /**
     * Runs one generation.
     *
     * @param projectId
     *            the project the seeds came from, recorded as provenance
     * @param scope
     *            the model context, already gathered and serialized on the editing context's own
     *            thread by the caller
     * @param instruction
     *            what the engineer asked for
     * @param author
     *            the origin of the content, recorded as provenance
     * @param task
     *            which task to run; supplies the prompt and the kind every candidate must be
     * @return what happened, including why there is nothing to review when there is nothing
     */
    public GenerationOutcome run(String projectId, SerializedModelScope scope, String instruction, String author, IAiTask task) {
        var request = task.prompt(scope, instruction);

        GenerationResult generation = this.generationClient.generate(request);
        GenerationOutcome outcome;
        if (generation.isUsable()) {
            outcome = this.review(projectId, instruction, author, generation, task);
        } else {
            outcome = this.nothingToReview(generation.explanationIfAny().orElse("Generation did not produce an answer: " + generation.outcome() + "."));
        }
        return outcome;
    }

    /**
     * Validates what came back and, if it holds up, stores it for review.
     */
    private GenerationOutcome review(String projectId, String instruction, String author, GenerationResult generation, IAiTask task) {
        // The task decides what a usable answer looks like: parsing and a type check for model
        // elements, something else entirely for prose.
        ValidationResult validation = task.validate(generation.text());

        GenerationOutcome outcome;
        if (!validation.isValid()) {
            // Nothing is persisted in this case. A failed run should leave no reviewable artefact:
            // storing candidates that failed validation would invite someone to apply them later.
            outcome = new GenerationOutcome(null, List.of(), validation.errors());
        } else if (validation.candidates().isEmpty()) {
            outcome = this.nothingToReview("The model did not propose anything from this context.");
        } else {
            var items = new ArrayList<AiProposalItem>();
            for (var candidate : validation.candidates()) {
                items.add(new AiProposalItem(candidate.label(), candidate.sysmlFragment()));
            }
            var proposal = new AiProposal(projectId, task.kind(), instruction, generation.modelId(), author, items);
            var saved = this.proposalRepository.save(proposal);
            var proposed = saved.getItems().stream()
                    .map(item -> new ProposedItem(item.getId().toString(), item.getLabel(), item.getSysmlFragment()))
                    .toList();
            outcome = new GenerationOutcome(saved.getId(), proposed, validation.warnings());
        }
        return outcome;
    }

    private GenerationOutcome nothingToReview(String problem) {
        return new GenerationOutcome(null, List.of(), List.of(problem));
    }
}
