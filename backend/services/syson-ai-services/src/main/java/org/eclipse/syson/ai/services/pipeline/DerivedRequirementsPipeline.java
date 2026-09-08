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

import org.eclipse.syson.ai.services.generation.DerivedRequirementsPromptFactory;
import org.eclipse.syson.ai.services.generation.GenerationResult;
import org.eclipse.syson.ai.services.generation.IGenerationClient;
import org.eclipse.syson.ai.services.proposal.AiProposal;
import org.eclipse.syson.ai.services.proposal.AiProposalItem;
import org.eclipse.syson.ai.services.proposal.IAiProposalRepository;
import org.eclipse.syson.ai.services.scope.SerializedModelScope;
import org.eclipse.syson.ai.services.validation.GeneratedSysmlValidator;
import org.eclipse.syson.ai.services.validation.ValidationResult;
import org.eclipse.syson.sysml.RequirementUsage;
import org.springframework.stereotype.Service;

/**
 * Runs the derived-requirements pipeline: scope, serialize, generate, validate, persist.
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
public class DerivedRequirementsPipeline {

    /** Recorded on the proposal so a later run of a different goal is distinguishable. */
    public static final String TASK_KIND = "derived-requirements";

    private final DerivedRequirementsPromptFactory promptFactory;

    private final IGenerationClient generationClient;

    private final GeneratedSysmlValidator validator;

    private final IAiProposalRepository proposalRepository;

    public DerivedRequirementsPipeline(DerivedRequirementsPromptFactory promptFactory, IGenerationClient generationClient, GeneratedSysmlValidator validator,
            IAiProposalRepository proposalRepository) {
        this.promptFactory = Objects.requireNonNull(promptFactory);
        this.generationClient = Objects.requireNonNull(generationClient);
        this.validator = Objects.requireNonNull(validator);
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
     *            who asked, recorded as provenance
     * @return what happened, including why there is nothing to review when there is nothing
     */
    public GenerationOutcome run(String projectId, SerializedModelScope scope, String instruction, String author) {
        var request = this.promptFactory.create(scope, instruction);

        GenerationResult generation = this.generationClient.generate(request);
        GenerationOutcome outcome;
        if (generation.isUsable()) {
            outcome = this.review(projectId, instruction, author, generation);
        } else {
            outcome = this.nothingToReview(generation.explanationIfAny().orElse("Generation did not produce an answer: " + generation.outcome() + "."));
        }
        return outcome;
    }

    /**
     * Validates what came back and, if it holds up, stores it for review.
     */
    private GenerationOutcome review(String projectId, String instruction, String author, GenerationResult generation) {
        ValidationResult validation = this.validator.validate(generation.text(), RequirementUsage.class);

        GenerationOutcome outcome;
        if (!validation.isValid()) {
            // Nothing is persisted in this case. A failed run should leave no reviewable artefact:
            // storing candidates that failed validation would invite someone to apply them later.
            outcome = new GenerationOutcome(null, 0, validation.errors());
        } else if (validation.candidates().isEmpty()) {
            outcome = this.nothingToReview("The model did not propose any requirements from this context.");
        } else {
            var items = new ArrayList<AiProposalItem>();
            for (var candidate : validation.candidates()) {
                items.add(new AiProposalItem(candidate.label(), candidate.sysmlFragment()));
            }
            var proposal = new AiProposal(projectId, TASK_KIND, instruction, generation.modelId(), author, items);
            var saved = this.proposalRepository.save(proposal);
            outcome = new GenerationOutcome(saved.getId(), items.size(), validation.warnings());
        }
        return outcome;
    }

    private GenerationOutcome nothingToReview(String problem) {
        return new GenerationOutcome(null, 0, List.of(problem));
    }
}
