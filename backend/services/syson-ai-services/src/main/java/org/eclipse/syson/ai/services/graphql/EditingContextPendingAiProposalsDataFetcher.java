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

import org.eclipse.sirius.components.annotations.spring.graphql.QueryDataFetcher;
import org.eclipse.sirius.components.graphql.api.IDataFetcherWithFieldCoordinates;
import org.eclipse.syson.ai.services.graphql.dto.AiProposalItemView;
import org.eclipse.syson.ai.services.graphql.dto.AiProposalView;
import org.eclipse.syson.ai.services.proposal.AiProposal;
import org.eclipse.syson.ai.services.proposal.AiProposalStatus;
import org.eclipse.syson.ai.services.proposal.IAiProposalRepository;

import graphql.schema.DataFetchingEnvironment;

/**
 * Data fetcher for {@code EditingContext#pendingAiProposals}.
 *
 * <p>
 * This is what makes a persisted proposal worth persisting: a generation that cost real money and
 * real time survives a refresh, and can be picked up by someone other than the person who asked
 * for it. Without a way to read one back, the proposal store would only ever be written to.
 * </p>
 *
 * <p>
 * It reads the database directly rather than going through {@code IEditingContextDispatcher}. A
 * proposal is not model data - it lives in its own tables and needs no editing context to be read -
 * so dispatching would queue a database query behind every pending edit on the project's
 * single-threaded executor for no benefit.
 * </p>
 *
 * <p>
 * The name says "pending" because that is what it returns. Applied and rejected proposals stay in
 * the database as history; a review UI that was handed those alongside live ones would offer to
 * apply work that has already been applied.
 * </p>
 *
 * @author DTPrendergast
 */
@QueryDataFetcher(type = "EditingContext", field = "pendingAiProposals")
public class EditingContextPendingAiProposalsDataFetcher implements IDataFetcherWithFieldCoordinates<List<AiProposalView>> {

    private final IAiProposalRepository proposalRepository;

    public EditingContextPendingAiProposalsDataFetcher(IAiProposalRepository proposalRepository) {
        this.proposalRepository = Objects.requireNonNull(proposalRepository);
    }

    @Override
    public List<AiProposalView> get(DataFetchingEnvironment environment) throws Exception {
        String editingContextId = environment.getSource();

        return this.proposalRepository.findAllByProjectIdOrderByCreatedOnDesc(editingContextId).stream()
                .filter(proposal -> proposal.getStatus() == AiProposalStatus.PENDING_REVIEW)
                .map(this::toView)
                .toList();
    }

    private AiProposalView toView(AiProposal proposal) {
        var items = proposal.getItems().stream()
                .map(item -> new AiProposalItemView(item.getId().toString(), item.getLabel(), item.getSysmlFragment(), item.getStatus().name()))
                .toList();

        return new AiProposalView(proposal.getId().toString(), proposal.getTaskKind(), proposal.getPrompt(), proposal.getModelId(), proposal.getCreatedBy(),
                proposal.getCreatedOn().toString(), proposal.getSeedElementIds(), items);
    }
}
