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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.eclipse.sirius.components.annotations.spring.graphql.QueryDataFetcher;
import org.eclipse.sirius.components.core.api.ErrorPayload;
import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.sirius.components.core.api.SuccessPayload;
import org.eclipse.sirius.components.graphql.api.IDataFetcherWithFieldCoordinates;
import org.eclipse.syson.ai.services.graphql.dto.RejectAiProposalItemInput;
import org.eclipse.syson.ai.services.proposal.AiProposal;
import org.eclipse.syson.ai.services.proposal.AiProposalItem;
import org.eclipse.syson.ai.services.proposal.AiProposalItemStatus;
import org.eclipse.syson.ai.services.proposal.IAiProposalRepository;

import graphql.schema.DataFetchingEnvironment;
import tools.jackson.databind.ObjectMapper;

/**
 * Data fetcher for the field Mutation#rejectAiProposalItem.
 *
 * <p>
 * Rejection used to live only in the review dialog's own state: turning a candidate down set a flag
 * on screen and nothing else, so the proposal stayed pending in the database and was offered again
 * the next time the dialog opened. Declining something has to be recorded, or it is not a decision.
 * </p>
 *
 * <p>
 * Nothing here touches the model - a rejected candidate is precisely one that never will - so this
 * writes to the proposal store directly rather than going through
 * {@code IEditingContextDispatcher}, which would queue a database write behind every pending edit
 * on the project's single-threaded executor for no reason.
 * </p>
 *
 * @author DTPrendergast
 */
@QueryDataFetcher(type = "Mutation", field = "rejectAiProposalItem")
public class MutationRejectAiProposalItemDataFetcher implements IDataFetcherWithFieldCoordinates<CompletableFuture<IPayload>> {

    private static final String INPUT_ARGUMENT = "input";

    private final ObjectMapper objectMapper;

    private final IAiProposalRepository proposalRepository;

    public MutationRejectAiProposalItemDataFetcher(ObjectMapper objectMapper, IAiProposalRepository proposalRepository) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.proposalRepository = Objects.requireNonNull(proposalRepository);
    }

    @Override
    public CompletableFuture<IPayload> get(DataFetchingEnvironment environment) throws Exception {
        Object argument = environment.getArgument(INPUT_ARGUMENT);
        var input = this.objectMapper.convertValue(argument, RejectAiProposalItemInput.class);

        return CompletableFuture.completedFuture(this.reject(input));
    }

    private IPayload reject(RejectAiProposalItemInput input) {
        IPayload payload = new ErrorPayload(input.id(), "That proposal or candidate no longer exists.");

        Optional<AiProposal> optionalProposal = this.findProposal(input.proposalId());
        if (optionalProposal.isPresent()) {
            AiProposal proposal = optionalProposal.get();
            Optional<AiProposalItem> optionalItem = proposal.getItems().stream()
                    .filter(item -> item.getId().toString().equals(input.itemId()))
                    .findFirst();
            if (optionalItem.isPresent()) {
                optionalItem.get().reject();
                // Close the proposal once nothing is left undecided, so a run that was entirely
                // turned down stops being offered as work to resume.
                boolean anythingLeft = proposal.getItems().stream().anyMatch(item -> item.getStatus() == AiProposalItemStatus.PENDING);
                if (!anythingLeft) {
                    proposal.reviewCompleted();
                }
                this.proposalRepository.save(proposal);
                payload = new SuccessPayload(input.id(), List.of());
            }
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
