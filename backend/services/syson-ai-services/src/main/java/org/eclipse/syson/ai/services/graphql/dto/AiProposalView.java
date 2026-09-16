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

import java.util.List;

/**
 * A stored proposal as the review UI reads it back.
 *
 * <p>
 * A projection rather than the entity: the entity carries UUIDs, {@code Instant}s and enums, none
 * of which GraphQL has, and exposing it directly would tie the wire format to the database schema.
 * </p>
 *
 * @param id
 *            the proposal id
 * @param taskKind
 *            which assistant task produced it
 * @param prompt
 *            what was asked for
 * @param modelId
 *            which model answered
 * @param createdBy
 *            the origin of the content
 * @param createdOn
 *            when it was generated, ISO-8601
 * @param seedElementIds
 *            the elements it was generated from, so a resumed review can be scoped to them
 * @param items
 *            the candidates and where each has got to
 *
 * @author DTPrendergast
 */
public record AiProposalView(String id, String taskKind, String prompt, String modelId, String createdBy, String createdOn, List<String> seedElementIds,
        List<AiProposalItemView> items) {

    public AiProposalView {
        seedElementIds = List.copyOf(seedElementIds);
        items = List.copyOf(items);
    }
}
