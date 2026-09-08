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
package org.eclipse.syson.ai.services.graphql;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.sirius.components.collaborative.api.ChangeDescription;
import org.eclipse.sirius.components.collaborative.api.IEditingContextEventHandler;
import org.eclipse.sirius.components.core.api.ErrorPayload;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.components.core.api.IObjectSearchService;
import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.sirius.components.core.api.SuccessPayload;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.components.representations.Message;
import org.eclipse.sirius.components.collaborative.api.ChangeKind;
import org.eclipse.syson.ai.services.graphql.dto.ApplyAiProposalItemInput;
import org.eclipse.syson.ai.services.pipeline.ProposalApplyService;
import org.eclipse.syson.ai.services.proposal.AiProposal;
import org.eclipse.syson.ai.services.proposal.AiProposalItem;
import org.eclipse.syson.ai.services.proposal.IAiProposalRepository;
import org.eclipse.syson.sysml.Element;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Sinks;

/**
 * Applies one accepted candidate to the model.
 *
 * <p>
 * A real mutation, and a fast one: it imports a fragment that has already been generated and
 * validated. Nothing here calls a model, which is what makes it safe on the project's
 * single-threaded executor.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
public class ApplyAiProposalItemEventHandler implements IEditingContextEventHandler {

    private final IObjectSearchService objectSearchService;

    private final IAiProposalRepository proposalRepository;

    private final ProposalApplyService applyService;

    public ApplyAiProposalItemEventHandler(IObjectSearchService objectSearchService, IAiProposalRepository proposalRepository, ProposalApplyService applyService) {
        this.objectSearchService = Objects.requireNonNull(objectSearchService);
        this.proposalRepository = Objects.requireNonNull(proposalRepository);
        this.applyService = Objects.requireNonNull(applyService);
    }

    @Override
    public boolean canHandle(IEditingContext editingContext, IInput input) {
        return input instanceof ApplyAiProposalItemInput;
    }

    @Override
    public void handle(Sinks.One<IPayload> payloadSink, Sinks.Many<ChangeDescription> changeDescriptionSink, IEditingContext editingContext, IInput input) {
        IPayload payload = new ErrorPayload(input.id(), "Could not apply the proposed element.");

        if (input instanceof ApplyAiProposalItemInput applyInput && editingContext instanceof IEMFEditingContext emfEditingContext) {
            payload = this.applyItem(applyInput, emfEditingContext, changeDescriptionSink, editingContext);
        }

        payloadSink.tryEmitValue(payload);
    }

    private IPayload applyItem(ApplyAiProposalItemInput input, IEMFEditingContext emfEditingContext, Sinks.Many<ChangeDescription> changeDescriptionSink,
            IEditingContext editingContext) {
        Optional<AiProposal> optionalProposal = this.findProposal(input.proposalId());
        Optional<Element> optionalParent = this.objectSearchService.getObject(editingContext, input.parentElementId())
                .filter(Element.class::isInstance)
                .map(Element.class::cast);

        IPayload payload = new ErrorPayload(input.id(), "The proposal, the item or the parent element could not be found.");
        if (optionalProposal.isPresent() && optionalParent.isPresent()) {
            AiProposal proposal = optionalProposal.get();
            Optional<AiProposalItem> optionalItem = proposal.getItems().stream()
                    .filter(item -> item.getId().toString().equals(input.itemId()))
                    .findFirst();
            if (optionalItem.isPresent()) {
                payload = this.doApply(input, emfEditingContext, changeDescriptionSink, editingContext, proposal, optionalItem.get(), optionalParent.get());
            }
        }
        return payload;
    }

    private IPayload doApply(ApplyAiProposalItemInput input, IEMFEditingContext emfEditingContext, Sinks.Many<ChangeDescription> changeDescriptionSink,
            IEditingContext editingContext, AiProposal proposal, AiProposalItem item, Element parent) {
        var messages = new ArrayList<Message>();
        boolean applied = this.applyService.apply(emfEditingContext, parent, proposal, item, messages);

        IPayload payload;
        if (applied) {
            // Persisting after the model write, so a proposal is never marked applied for an
            // element that did not actually land.
            this.proposalRepository.save(proposal);
            changeDescriptionSink.tryEmitNext(new ChangeDescription(ChangeKind.SEMANTIC_CHANGE, editingContext.getId(), input));
            payload = new SuccessPayload(input.id(), messages);
        } else {
            payload = new ErrorPayload(input.id(), "The proposed element could not be applied.", messages);
        }
        return payload;
    }

    private Optional<AiProposal> findProposal(String proposalId) {
        Optional<AiProposal> result = Optional.empty();
        try {
            result = this.proposalRepository.findById(UUID.fromString(proposalId));
        } catch (IllegalArgumentException exception) {
            result = Optional.empty();
        }
        return result;
    }
}
