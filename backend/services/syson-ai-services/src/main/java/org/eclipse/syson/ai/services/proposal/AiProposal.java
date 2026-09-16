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
package org.eclipse.syson.ai.services.proposal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/**
 * What one assistant run produced, held for human review.
 *
 * <p>
 * This is persisted rather than kept in memory for three reasons: an expensive generation survives
 * a page refresh or a backend restart; the engineer who reviews a proposal need not be the one who
 * requested it; and unattended generation becomes possible later without redesigning the pipeline.
 * Nothing here has entered the model - applying is a separate, explicit step.
 * </p>
 *
 * <p>
 * The prompt, model identifier, author and timestamps are not bookkeeping: they are the provenance
 * that has to survive onto generated elements, so that a derived requirement which reached a
 * baseline can be traced back to the run that proposed it.
 * </p>
 *
 * @author DTPrendergast
 */
@Table("ai_proposal")
public class AiProposal implements Persistable<UUID> {

    @Id
    private UUID id;

    private String projectId;

    private String taskKind;

    private AiProposalStatus status;

    private String prompt;

    private String modelId;

    private String createdBy;

    /**
     * The elements the generation was run from, comma separated.
     *
     * <p>
     * Kept so that resuming an unreviewed proposal can be scoped to the element the reviewer
     * actually has open. Candidates derived from one element applied to another would be wrong in
     * every task and destructive in the one that replaces a narrative.
     * </p>
     */
    private String seedElementIds = "";

    @MappedCollection(idColumn = "ai_proposal_id", keyColumn = "item_index")
    private List<AiProposalItem> items = new ArrayList<>();

    private Instant createdOn;

    private Instant lastModifiedOn;

    @Transient
    private boolean isNew;

    public AiProposal() {
        // Required by Spring Data JDBC, which prefers the no-arg constructor when a class declares
        // several, so materializing a row never runs the constructor below.
    }

    /**
     * Creates a proposal that is waiting for review, not yet persisted.
     *
     * @param projectId
     *            the project the proposal was generated against
     * @param taskKind
     *            which assistant task produced it, e.g. derived requirements
     * @param prompt
     *            what the user asked for, kept as provenance
     * @param modelId
     *            the model that generated it, kept as provenance
     * @param createdBy
     *            who requested it, kept as provenance
     * @param seedElementIds
     *            the elements the generation was run from
     * @param items
     *            the candidates offered for review
     */
    public AiProposal(String projectId, String taskKind, String prompt, String modelId, String createdBy, List<String> seedElementIds, List<AiProposalItem> items) {
        this.id = UUID.randomUUID();
        this.projectId = Objects.requireNonNull(projectId);
        this.taskKind = Objects.requireNonNull(taskKind);
        this.prompt = Objects.requireNonNull(prompt);
        this.modelId = Objects.requireNonNull(modelId);
        this.createdBy = Objects.requireNonNull(createdBy);
        this.seedElementIds = String.join(",", Objects.requireNonNull(seedElementIds));
        this.items = new ArrayList<>(Objects.requireNonNull(items));
        this.status = AiProposalStatus.PENDING_REVIEW;
        this.createdOn = Instant.now();
        this.lastModifiedOn = this.createdOn;
        this.isNew = true;
    }

    @Override
    public UUID getId() {
        return this.id;
    }

    @Override
    public boolean isNew() {
        return this.isNew;
    }

    public String getProjectId() {
        return this.projectId;
    }

    public String getTaskKind() {
        return this.taskKind;
    }

    public AiProposalStatus getStatus() {
        return this.status;
    }

    public String getPrompt() {
        return this.prompt;
    }

    public String getModelId() {
        return this.modelId;
    }

    public String getCreatedBy() {
        return this.createdBy;
    }

    /**
     * The elements this proposal was generated from.
     *
     * @return the seed element identifiers, empty for proposals created before they were recorded
     */
    public List<String> getSeedElementIds() {
        List<String> ids = List.of();
        if (this.seedElementIds != null && !this.seedElementIds.isBlank()) {
            ids = List.of(this.seedElementIds.split(","));
        }
        return ids;
    }

    public List<AiProposalItem> getItems() {
        return this.items;
    }

    public Instant getCreatedOn() {
        return this.createdOn;
    }

    public Instant getLastModifiedOn() {
        return this.lastModifiedOn;
    }

    /**
     * Closes the review, deriving the proposal's status from what the reviewer decided item by item
     * rather than taking it as an argument, so the two can never disagree.
     */
    public void reviewCompleted() {
        boolean anyApplied = this.items.stream().anyMatch(item -> item.getStatus() == AiProposalItemStatus.APPLIED);
        if (anyApplied) {
            this.status = AiProposalStatus.APPLIED;
        } else {
            this.status = AiProposalStatus.REJECTED;
        }
        this.lastModifiedOn = Instant.now();
    }

    /**
     * Records that this run failed - generation errored, or what came back was not valid SysML v2.
     */
    public void failed() {
        this.status = AiProposalStatus.FAILED;
        this.lastModifiedOn = Instant.now();
    }
}
