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

import java.util.List;
import java.util.Objects;

import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.components.representations.Message;
import org.eclipse.sirius.components.representations.MessageLevel;
import org.eclipse.syson.ai.services.proposal.AiProposal;
import org.eclipse.syson.ai.services.proposal.AiProposalItem;
import org.eclipse.syson.ai.services.scope.SerializedModelScope;
import org.eclipse.syson.ai.services.validation.GeneratedCandidate;
import org.eclipse.syson.ai.services.validation.ValidationResult;
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.UseCaseUsage;
import org.springframework.stereotype.Service;

/**
 * Fills in the details of one use case.
 *
 * <p>
 * Distinct from {@code DraftUseCasesTask}, which proposes the <em>set</em> of use cases implied by
 * an operational concept. This one starts from a use case that already exists and writes what it
 * actually involves: who performs it, what starts it, what happens, and how it ends.
 * </p>
 *
 * <p>
 * Its answer is prose, like the narrative task, so parsing it would prove nothing and applying it
 * replaces the use case's documentation rather than adding elements.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
public class DetailUseCaseTask implements IAiTask {

    public static final String KIND = "detail-use-case";

    private static final String SYSTEM_PROMPT = """
            You are helping a systems engineer flesh out a single use case in a SysML v2 model. The
            engineer has already decided which use cases exist and what this one is called; your job
            is to describe what it involves, not to propose different use cases.

            You are given the use case, whatever documentation it already has, and the surrounding
            model - which usually includes the operational concept narrative and the other use
            cases.

            Write its details as prose, covering what the model supports:
            - who performs it, and who else is involved or affected
            - what starts it
            - what happens, in order, including the decisions taken along the way
            - how it ends, and how you can tell it succeeded
            - what has to be true beforehand, and what is left true afterwards
            - where it can go wrong, when the model gives you grounds to say so

            Use `## ` sub-headings where that helps a reader, and prose under them rather than bare
            labels. A heading needs a space after the hashes or it is not a heading.

            Stay inside the model. The operator, systems, environment and constraints are there to
            be used; do not introduce new ones, and do not invent numeric values, timings or
            interfaces that appear nowhere. Where the model leaves something genuinely open, say
            what depends on it rather than inventing an answer.

            Reply with the details themselves and nothing else. No preamble, no explanation of your
            reasoning, no code fences. What you write is shown to the engineer and replaces this use
            case's documentation verbatim if they accept it.
            """;

    private final TaskPrompts prompts;

    private final ElementDocumentationService documentationService;

    public DetailUseCaseTask(TaskPrompts prompts, ElementDocumentationService documentationService) {
        this.prompts = Objects.requireNonNull(prompts);
        this.documentationService = Objects.requireNonNull(documentationService);
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public GenerationRequest prompt(SerializedModelScope scope, String instruction) {
        return new GenerationRequest(SYSTEM_PROMPT, this.prompts.userPrompt(scope, instruction));
    }

    @Override
    public ValidationResult validate(String rawResponse) {
        String text = "";
        if (rawResponse != null) {
            text = rawResponse;
        }
        String stripped = this.prompts.stripCodeFences(text);
        boolean fencesStripped = !stripped.equals(text.strip());

        List<GeneratedCandidate> candidates = List.of();
        if (!stripped.isBlank()) {
            candidates = List.of(new GeneratedCandidate("Use case details", stripped));
        }
        return new ValidationResult(candidates, List.of(), List.of(), fencesStripped);
    }

    @Override
    public boolean apply(IEMFEditingContext editingContext, Element target, AiProposal proposal, AiProposalItem item, List<Message> messages) {
        boolean applied = false;
        if (target instanceof UseCaseUsage) {
            applied = this.documentationService.setDocumentation(target, item.getSysmlFragment());
        } else {
            // Guarded rather than assumed: the menu entry only appears on a use case, but a
            // resumed proposal could be applied from elsewhere, and writing these details onto
            // something else would overwrite unrelated documentation.
            messages.add(new Message("That element is not a use case, so there is no use case documentation to replace.", MessageLevel.ERROR));
        }
        return applied;
    }
}
