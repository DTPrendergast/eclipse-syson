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
package org.eclipse.syson.ai.services.generation;

import java.util.Optional;

/**
 * What came back from a generation attempt.
 *
 * <p>
 * A refusal is a successful HTTP call, not an exception: the API returns 200 with a
 * {@code refusal} stop reason. Callers that only check for thrown exceptions would treat a refusal
 * as an empty answer, so it is modelled explicitly here.
 * </p>
 *
 * @param text
 *            what the model produced; empty when the request was refused or the assistant is not
 *            configured
 * @param modelId
 *            which model actually answered, recorded as provenance on anything the user accepts
 * @param outcome
 *            how the attempt ended
 * @param explanation
 *            why, when the outcome is not {@link Outcome#ANSWERED}
 *
 * @author DTPrendergast
 */
public record GenerationResult(String text, String modelId, Outcome outcome, String explanation) {

    /**
     * How a generation attempt ended.
     *
     * @author DTPrendergast
     */
    public enum Outcome {
        /** The model answered. */
        ANSWERED,
        /** The model declined on safety grounds; {@code stop_reason} was {@code refusal}. */
        REFUSED,
        /** The response hit the token ceiling and is incomplete, so it must not be parsed as-is. */
        TRUNCATED,
        /** No API key is configured, so nothing was sent. */
        NOT_CONFIGURED
    }

    public boolean isUsable() {
        return this.outcome == Outcome.ANSWERED;
    }

    public Optional<String> explanationIfAny() {
        return Optional.ofNullable(this.explanation);
    }
}
