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
package org.eclipse.syson.ai.services.graphql.dto;

import java.util.UUID;

import org.eclipse.sirius.components.core.api.IInput;

/**
 * Records that a reviewer turned a candidate down.
 *
 * @param id
 *            the mutation id
 * @param proposalId
 *            the proposal the item belongs to
 * @param itemId
 *            the candidate being rejected
 *
 * @author DTPrendergast
 */
public record RejectAiProposalItemInput(UUID id, String proposalId, String itemId) implements IInput {
}
