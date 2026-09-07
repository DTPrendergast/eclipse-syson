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
package org.eclipse.syson.ai.services.pipeline;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What one run of the pipeline produced.
 *
 * <p>
 * A run that produces no proposal is not an exception. The model may decline, may return
 * something that is not SysML, or may correctly decide there is nothing to derive - all of which a
 * user needs to be told about in the same way, and none of which should fail the request that
 * triggered the run.
 * </p>
 *
 * @param proposalId
 *            the persisted proposal, present only when there is something to review
 * @param candidateCount
 *            how many candidates were offered
 * @param problems
 *            why there is nothing to review, when there is nothing to review
 *
 * @author DTPrendergast
 */
public record GenerationOutcome(UUID proposalId, int candidateCount, List<String> problems) {

    public GenerationOutcome {
        problems = List.copyOf(problems);
    }

    public Optional<UUID> proposal() {
        return Optional.ofNullable(this.proposalId);
    }

    public boolean hasSomethingToReview() {
        return this.proposalId != null;
    }
}
