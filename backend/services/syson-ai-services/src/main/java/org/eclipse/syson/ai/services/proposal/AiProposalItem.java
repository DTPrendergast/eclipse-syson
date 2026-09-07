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

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One candidate element within a proposal, carrying the textual SysML v2 fragment that would create
 * it. Fragments are stored as text rather than as EMF structure on purpose: the write path
 * re-imports them through {@code ISysMLTextImporter}, so the parser validates what the model
 * produced instead of us trusting it.
 *
 * <p>
 * The owning {@link AiProposal} manages this row's foreign key and ordering column, so neither is
 * a field here - that is how Spring Data JDBC maps a child collection.
 * </p>
 *
 * @author DTPrendergast
 */
@Table("ai_proposal_item")
public class AiProposalItem {

    @Id
    private UUID id;

    private String label;

    private String sysmlFragment;

    private AiProposalItemStatus status;

    private String appliedElementId;

    public AiProposalItem() {
        // Required by Spring Data JDBC, which prefers the no-arg constructor when a class declares
        // several, so materializing a row never runs the constructor below.
    }

    /**
     * Creates an item awaiting a per-item accept or reject decision.
     *
     * @param label
     *            how the candidate is shown in the review UI
     * @param sysmlFragment
     *            the candidate as textual SysML v2, validated by the parser before it is applied
     */
    public AiProposalItem(String label, String sysmlFragment) {
        this.id = UUID.randomUUID();
        this.label = Objects.requireNonNull(label);
        this.sysmlFragment = Objects.requireNonNull(sysmlFragment);
        this.status = AiProposalItemStatus.PENDING;
    }

    public UUID getId() {
        return this.id;
    }

    public String getLabel() {
        return this.label;
    }

    public String getSysmlFragment() {
        return this.sysmlFragment;
    }

    public AiProposalItemStatus getStatus() {
        return this.status;
    }

    /**
     * The identifier of the element this item created, present only once the item has actually been
     * applied to the model.
     *
     * @return the applied element identifier, or empty if this item has not been applied
     */
    public Optional<String> getAppliedElementId() {
        return Optional.ofNullable(this.appliedElementId);
    }

    public void accept() {
        this.status = AiProposalItemStatus.ACCEPTED;
    }

    public void reject() {
        this.status = AiProposalItemStatus.REJECTED;
    }

    /**
     * Records that this item has been written to the model.
     *
     * @param elementId
     *            the identifier of the element that was created
     */
    public void applied(String elementId) {
        this.appliedElementId = Objects.requireNonNull(elementId);
        this.status = AiProposalItemStatus.APPLIED;
    }
}
