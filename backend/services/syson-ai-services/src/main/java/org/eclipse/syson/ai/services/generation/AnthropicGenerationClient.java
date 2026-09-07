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

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Talks to the Anthropic API.
 *
 * <p>
 * The client is built lazily and only once a key is configured, so the application starts
 * perfectly well without credentials - the assistant just reports itself as unconfigured rather
 * than failing at boot.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
public class AnthropicGenerationClient implements IGenerationClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnthropicGenerationClient.class);

    private final SysONAiProperties properties;

    private AnthropicClient client;

    public AnthropicGenerationClient(SysONAiProperties properties) {
        this.properties = properties;
    }

    @Override
    public GenerationResult generate(GenerationRequest request) {
        if (!this.properties.isConfigured()) {
            return new GenerationResult("", this.properties.getModel(), GenerationResult.Outcome.NOT_CONFIGURED,
                    "No API key configured; set syson.ai.api-key (typically from the ANTHROPIC_API_KEY environment variable).");
        }

        MessageCreateParams params = MessageCreateParams.builder()
                .model(this.properties.getModel())
                .maxTokens(this.properties.getMaxTokens())
                .system(request.systemPrompt())
                .addUserMessage(request.userPrompt())
                .build();

        GenerationResult result;
        try {
            result = this.interpret(this.anthropicClient().messages().create(params));
        } catch (AnthropicServiceException exception) {
            // Logged rather than rethrown: a failed generation is an outcome the review UI has to
            // show, not a reason to fail the request that triggered it.
            LOGGER.warn("Generation failed", exception);
            result = new GenerationResult("", this.properties.getModel(), GenerationResult.Outcome.REFUSED, exception.getMessage());
        }
        return result;
    }

    /**
     * Turns a response into an outcome. A refusal and a truncation both arrive as ordinary
     * successful responses, so both are checked before the text is trusted.
     */
    private GenerationResult interpret(Message message) {
        String model = message.model().toString();
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : message.content()) {
            block.text().ifPresent(textBlock -> text.append(textBlock.text()));
        }

        GenerationResult.Outcome outcome = GenerationResult.Outcome.ANSWERED;
        String explanation = null;
        StopReason stopReason = message.stopReason().orElse(null);
        if (stopReason == StopReason.REFUSAL) {
            outcome = GenerationResult.Outcome.REFUSED;
            explanation = "The model declined to answer this request.";
        } else if (stopReason == StopReason.MAX_TOKENS) {
            // The text is a fragment. Parsing it would silently drop whatever was cut off, so it is
            // reported as unusable instead.
            outcome = GenerationResult.Outcome.TRUNCATED;
            explanation = "The response hit the " + this.properties.getMaxTokens() + " token ceiling and is incomplete.";
        }

        return new GenerationResult(text.toString(), model, outcome, explanation);
    }

    private AnthropicClient anthropicClient() {
        if (this.client == null) {
            this.client = AnthropicOkHttpClient.builder()
                    .apiKey(this.properties.getApiKey())
                    .build();
        }
        return this.client;
    }
}
