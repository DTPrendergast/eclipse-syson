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
package org.eclipse.syson.ai.services.validation;

import java.util.List;

/**
 * The outcome of checking a model's response before any of it is offered for review.
 *
 * @param candidates
 *            the individually reviewable elements found, in the order the model produced them
 * @param errors
 *            what makes the response unusable; non-empty means nothing should be offered
 * @param warnings
 *            what is worth showing a reviewer but does not invalidate the response
 * @param fencesStripped
 *            true when the response arrived wrapped in Markdown code fences and they were removed
 *            before parsing. Recorded rather than hidden: it means the model ignored an explicit
 *            instruction, which is worth knowing when tuning the prompt.
 *
 * @author DTPrendergast
 */
public record ValidationResult(List<GeneratedCandidate> candidates, List<String> errors, List<String> warnings, boolean fencesStripped) {

    public ValidationResult {
        candidates = List.copyOf(candidates);
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
    }

    /**
     * Whether anything may be offered for review.
     *
     * <p>
     * An empty but error-free response is valid. The prompt tells the model to emit nothing rather
     * than guess when the context is too thin, so "no candidates" is a legitimate answer and must
     * not be reported as a failure.
     * </p>
     *
     * @return true when the response parsed cleanly
     */
    public boolean isValid() {
        return this.errors.isEmpty();
    }
}
