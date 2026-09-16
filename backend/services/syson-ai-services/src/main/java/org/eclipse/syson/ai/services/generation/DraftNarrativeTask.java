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
import org.eclipse.syson.operational.concept.library.OperationalConceptNarrativeService;
import org.eclipse.syson.sysml.Element;
import org.springframework.stereotype.Service;

/**
 * Turns an operational concept's notes into prose.
 *
 * <p>
 * This is the task that does not fit the shape of the others, and it is worth being explicit about
 * why. Its answer is <em>prose</em>, not model elements. So parsing it would prove nothing - SysML
 * v2 treats a bare name as a declaration, which means any English sentence parses cleanly - and
 * applying it does not add anything to the model: it replaces the narrative that is already there.
 * Both of those are why {@link IAiTask} owns validation and application rather than the pipeline.
 * </p>
 *
 * <p>
 * Because it replaces rather than adds, the review step matters more here than anywhere else: the
 * candidate shown to the reviewer is the exact text that will overwrite their narrative, and
 * nothing happens until they accept it.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
public class DraftNarrativeTask implements IAiTask {

    public static final String KIND = "draft-narrative";

    private static final String SYSTEM_PROMPT = """
            You are helping a systems engineer turn working notes into the narrative of an \
            operational concept: a description of who uses a system and how they intend to use it. \
            It is read by engineers, customers and reviewers who were not in the room.

            You are given their current narrative, which may be rough - bullet points, fragments, \
            half-finished sentences - along with the surrounding model.

            Write a connected document, not a list of sentences:
            - Group related points into paragraphs. A paragraph should make one point and develop \
            it; several related bullets usually belong in one paragraph, not one each.
            - Open each section with a sentence that says what it is about, then develop it.
            - Draw out what the notes imply: sequence, causes, who depends on whom, why a \
            constraint matters. Saying that a datalink range bounds where the aircraft can be \
            tasked is elaboration, not invention.
            - Carry over every point. You may reorder and combine them, and you may say more about \
            each than the note did.
            - Follow any length or structure the engineer asks for. If they ask for a page, write a \
            page; do not return four sentences.

            The line between elaboration and invention: you may explain, connect and expand on what \
            is there, and you may use the model for context. You may not introduce operators, \
            systems, environments, numeric values or constraints that appear nowhere in the notes \
            or the model. If something is genuinely unclear, write around it rather than guessing \
            at specifics - and never leave a bullet list where prose was asked for.

            Keep their Markdown headings, and use `## ` for sub-sections. A heading needs a space \
            after the hashes or it is not a heading.

            Reply with the narrative itself and nothing else. No preamble, no explanation of what \
            you changed, no code fences. What you write is shown to the engineer and replaces their \
            narrative verbatim if they accept it.
            """;

    private final TaskPrompts prompts;

    private final OperationalConceptNarrativeService narrativeService;

    public DraftNarrativeTask(TaskPrompts prompts, OperationalConceptNarrativeService narrativeService) {
        this.prompts = Objects.requireNonNull(prompts);
        this.narrativeService = Objects.requireNonNull(narrativeService);
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public GenerationRequest prompt(SerializedModelScope scope, String instruction) {
        return new GenerationRequest(SYSTEM_PROMPT, this.prompts.userPrompt(scope, instruction));
    }

    /**
     * Accepts the response as prose.
     *
     * <p>
     * There is no parsing here on purpose. The only checks that mean anything for prose are that
     * there is some, and that the model did not wrap it in a code fence despite being told not to.
     * </p>
     */
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
            candidates = List.of(new GeneratedCandidate("Rewritten narrative", stripped));
        }
        return new ValidationResult(candidates, List.of(), List.of(), fencesStripped);
    }

    /**
     * Replaces the narrative.
     *
     * <p>
     * No provenance stamp: the narrative is a string on an element that already exists, not a new
     * element, so there is nothing to annotate. The proposal row keeps the prompt, the model and
     * the timestamp, and a reviewer accepted the text knowingly.
     * </p>
     */
    @Override
    public boolean apply(IEMFEditingContext editingContext, Element target, AiProposal proposal, AiProposalItem item, List<Message> messages) {
        boolean written = this.narrativeService.setNarrative(target, item.getSysmlFragment());
        if (written) {
            item.applied(target.getElementId());
        } else {
            messages.add(new Message("That element is not an operational concept, so there is no narrative to replace.", MessageLevel.ERROR));
        }
        return written;
    }

}
