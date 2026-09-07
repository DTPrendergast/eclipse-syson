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

import java.util.Objects;

/**
 * One request to the model: what it is being asked to do, and the model context it may use.
 *
 * @param systemPrompt
 *            the standing instructions - the role, the output contract, the rules. Kept separate
 *            from {@code userPrompt} because it is stable across requests, which is what makes it
 *            cacheable later.
 * @param userPrompt
 *            the specific ask, including the serialized model scope
 *
 * @author DTPrendergast
 */
public record GenerationRequest(String systemPrompt, String userPrompt) {

    public GenerationRequest {
        Objects.requireNonNull(systemPrompt);
        Objects.requireNonNull(userPrompt);
    }
}
