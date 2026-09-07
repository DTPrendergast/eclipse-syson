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

/**
 * Lifecycle of a single candidate within a proposal. Individual accept/reject is a requirement of
 * this feature, so status lives per item rather than only on the proposal.
 *
 * @author DTPrendergast
 */
public enum AiProposalItemStatus {
    /**
     * Not yet decided by a reviewer.
     */
    PENDING,
    /**
     * Accepted by a reviewer but not yet written to the model.
     */
    ACCEPTED,
    /**
     * Accepted and successfully applied to the model; the created element is recorded on the item.
     */
    APPLIED,
    /**
     * Rejected by a reviewer. It stays in the proposal as a record of what was offered.
     */
    REJECTED;
}
