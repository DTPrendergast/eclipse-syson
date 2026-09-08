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
import java.util.UUID;

import org.eclipse.sirius.components.core.api.IPayload;

/**
 * The result of asking for derived requirements.
 *
 * <p>
 * A run that produced nothing is still a successful mutation: the model may decline, or may
 * correctly find nothing to derive. Those are reported in {@code problems} rather than as errors,
 * so the client can tell the user what happened instead of showing a failure.
 * </p>
 *
 * @param id
 *            the mutation id
 * @param proposalId
 *            the proposal to review, null when there is nothing to review
 * @param candidateCount
 *            how many candidates are waiting
 * @param problems
 *            why there is nothing to review, when there is nothing
 *
 * @author DTPrendergast
 */
public record GenerateDerivedRequirementsSuccessPayload(UUID id, String proposalId, int candidateCount, List<String> problems) implements IPayload {

    public GenerateDerivedRequirementsSuccessPayload {
        problems = List.copyOf(problems);
    }
}
