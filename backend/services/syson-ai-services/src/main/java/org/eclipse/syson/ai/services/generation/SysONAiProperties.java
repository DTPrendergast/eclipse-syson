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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the AI modeling assistant.
 *
 * <p>
 * The API key is deliberately not defaulted to anything. It is read from
 * {@code syson.ai.api-key}, which should itself be wired to an environment variable
 * ({@code ${ANTHROPIC_API_KEY:}}) rather than written into a properties file - plan.md item 4
 * lists "API credentials must not be committed" as a non-negotiable. With no key configured the
 * assistant is simply switched off; the application still starts, which is what lets the rest of
 * the tool be developed and tested without credentials.
 * </p>
 *
 * @author DTPrendergast
 */
@ConfigurationProperties(prefix = "syson.ai")
public class SysONAiProperties {

    /** The Anthropic API key. Empty means the assistant is disabled. */
    private String apiKey = "";

    /** The model used for generation. */
    private String model = "claude-opus-5";

    /**
     * The response ceiling. 16000 is the documented default for non-streaming requests: high
     * enough that a set of derived requirements is not truncated mid-thought, low enough to stay
     * inside the SDK's HTTP timeout.
     */
    private long maxTokens = 16000;

    public String getApiKey() {
        return this.apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return this.model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public long getMaxTokens() {
        return this.maxTokens;
    }

    public void setMaxTokens(long maxTokens) {
        this.maxTokens = maxTokens;
    }

    /**
     * Whether a key has been configured at all.
     *
     * @return true when generation can be attempted
     */
    public boolean isConfigured() {
        return this.apiKey != null && !this.apiKey.isBlank();
    }
}
