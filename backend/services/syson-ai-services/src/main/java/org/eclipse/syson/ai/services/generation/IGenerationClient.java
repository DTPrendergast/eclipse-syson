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

/**
 * Sends a prompt to a model and returns what came back.
 *
 * <p>
 * An interface so that the pipeline above it can be tested without a network call or an API key.
 * </p>
 *
 * @author DTPrendergast
 */
public interface IGenerationClient {

    /**
     * Runs one generation.
     *
     * @param request
     *            what to ask
     * @return the outcome, which may be a refusal or a "not configured" rather than an answer
     */
    GenerationResult generate(GenerationRequest request);
}
