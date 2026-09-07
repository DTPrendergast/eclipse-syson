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
 * Lifecycle of a whole proposal. A proposal is never applied implicitly: it stays
 * {@link #PENDING_REVIEW} until a human decides item by item, which is what makes the mandatory
 * review gate a property of the data model rather than of a particular user interface.
 *
 * @author DTPrendergast
 */
public enum AiProposalStatus {
    /**
     * Generated and waiting for a human to review it. Nothing has entered the model.
     */
    PENDING_REVIEW,
    /**
     * The reviewer is done and at least one item was applied to the model.
     */
    APPLIED,
    /**
     * The reviewer is done and rejected every item; nothing entered the model.
     */
    REJECTED,
    /**
     * Generation itself failed, or what came back could not be parsed as valid SysML v2. Kept
     * rather than discarded so that a failed run is still auditable.
     */
    FAILED;
}
