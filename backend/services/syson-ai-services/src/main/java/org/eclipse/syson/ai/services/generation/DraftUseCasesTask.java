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

import org.eclipse.syson.ai.services.pipeline.ProposalApplyService;
import org.eclipse.syson.ai.services.proposal.AiProposal;
import org.eclipse.syson.ai.services.proposal.AiProposalItem;
import org.eclipse.syson.ai.services.scope.SerializedModelScope;
import org.eclipse.syson.ai.services.validation.GeneratedSysmlValidator;
import org.eclipse.syson.ai.services.validation.ValidationResult;
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.UseCaseUsage;
import org.springframework.stereotype.Service;

/**
 * Drafts use cases from an operational concept (plan.md item 4, goal 1 territory).
 *
 * <p>
 * Aimed at the operational-concept packages item 2 introduced: a narrative describing who uses the
 * system and how, from which concrete use cases can be drawn. The narrative reaches the model
 * because the scope serializer emits {@code doc} bodies inline.
 * </p>
 *
 * <p>
 * {@code use case} usages only, not {@code use case def}. Both parse cleanly - checked - but an
 * operational concept describes what people actually do rather than defining reusable types, and
 * allowing either would mean a reviewer accepting candidates of two different kinds from one run.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
public class DraftUseCasesTask implements IAiTask {

    public static final String KIND = "draft-use-cases";

    private static final String SYSTEM_PROMPT = """
            You are assisting a systems engineer working in SysML v2. Your job is to draft \
            candidate use cases from an operational concept: a narrative describing who uses a \
            system and how they intend to use it.

            - Emit one `use case` declaration per candidate. Use `use case`, never `use case def`.
            - Give each a `doc` comment saying, in one or two sentences, who is doing what and to \
            what end.
            - Name each after the goal being achieved, not the mechanism - `restorePower`, not \
            `pressRestoreButton`.
            - Cover what the narrative actually describes. Where it implies an actor or a step \
            without saying so outright, a use case is justified; where it says nothing, do not \
            invent one.
            """ + TaskPrompts.COMMON_RULES;

    private final TaskPrompts prompts;

    private final GeneratedSysmlValidator validator;

    private final ProposalApplyService applyService;

    public DraftUseCasesTask(TaskPrompts prompts, GeneratedSysmlValidator validator, ProposalApplyService applyService) {
        this.prompts = Objects.requireNonNull(prompts);
        this.validator = Objects.requireNonNull(validator);
        this.applyService = Objects.requireNonNull(applyService);
    }

    @Override
    public String kind() {
        return KIND;
    }

    /**
     * The gate that rejects prose. SysML v2 treats a bare name as an element declaration, so
     * English parses cleanly - the parse proves nothing, and this type check is what decides
     * whether a response is what was asked for.
     */
    @Override
    public ValidationResult validate(String rawResponse) {
        return this.validator.validate(rawResponse, UseCaseUsage.class);
    }

    @Override
    public boolean apply(IEMFEditingContext editingContext, Element target, AiProposal proposal, AiProposalItem item, List<Message> messages) {
        return this.applyService.apply(editingContext, target, proposal, item, messages);
    }

    @Override
    public GenerationRequest prompt(SerializedModelScope scope, String instruction) {
        return new GenerationRequest(SYSTEM_PROMPT, this.prompts.userPrompt(scope, instruction));
    }
}
