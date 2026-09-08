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
 * Applies one candidate a reviewer has accepted.
 *
 * @param id
 *            the mutation id
 * @param editingContextId
 *            the project to write into
 * @param proposalId
 *            the proposal the item belongs to
 * @param itemId
 *            which candidate to apply
 * @param parentElementId
 *            the element the new element is created under
 *
 * @author DTPrendergast
 */
public record ApplyAiProposalItemInput(UUID id, String editingContextId, String proposalId, String itemId, String parentElementId) implements IInput {
}
