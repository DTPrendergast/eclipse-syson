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
import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;

import java.util.List;
import java.util.Map;

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

    /** The beta this fork sends for server-side refusal fallbacks, in the array form. */
    private static final String FALLBACK_BETA = "server-side-fallback-2026-06-01";

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

        MessageCreateParams params = this.paramsFor(request);

        GenerationResult result;
        try {
            result = this.interpret(this.anthropicClient().beta().messages().create(params));
        } catch (AnthropicServiceException exception) {
            // Logged rather than rethrown: a failed generation is an outcome the review UI has to
            // show, not a reason to fail the request that triggered it.
            LOGGER.warn("Generation failed", exception);
            result = new GenerationResult("", this.properties.getModel(), GenerationResult.Outcome.REFUSED, exception.getMessage());
        }
        return result;
    }

    /**
     * Builds the request.
     *
     * <p>
     * Package-private so a test can assert what is actually sent. The fallback below cannot be
     * exercised without a real API key, so what can be checked without one - that the beta flag and
     * the body property are present and correctly shaped - is checked.
     * </p>
     */
    MessageCreateParams paramsFor(GenerationRequest request) {
        var builder = MessageCreateParams.builder()
                .model(this.properties.getModel())
                .maxTokens(this.properties.getMaxTokens())
                .system(request.systemPrompt())
                .addUserMessage(request.userPrompt());

        if (this.properties.hasFallbackModel()) {
            // Server-side refusal fallback: on a policy decline the API re-runs the same request on
            // the fallback model within the same call, instead of returning nothing. The SDK at
            // this version has no typed binding for it - there is no fallback class anywhere in
            // anthropic-java 2.34.0 and no such constant on AnthropicBeta - so it goes on as a raw
            // body property using the documented wire shape. If a later SDK adds the typed API,
            // this is the thing to replace.
            builder = builder
                    .addBeta(FALLBACK_BETA)
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from(List.of(Map.of("model", this.properties.getFallbackModel()))));
        }

        return builder.build();
    }

    /**
     * Turns a response into an outcome. A refusal and a truncation both arrive as ordinary
     * successful responses, so both are checked before the text is trusted.
     */
    private GenerationResult interpret(BetaMessage message) {
        String model = message.model().toString();
        StringBuilder text = new StringBuilder();
        for (BetaContentBlock block : message.content()) {
            block.text().ifPresent(textBlock -> text.append(textBlock.text()));
        }

        GenerationResult.Outcome outcome = GenerationResult.Outcome.ANSWERED;
        String explanation = null;
        BetaStopReason stopReason = message.stopReason().orElse(null);
        if (stopReason == BetaStopReason.REFUSAL) {
            // The chain refused: with a fallback configured this means the fallback model declined
            // too, not that the fallback never ran.
            outcome = GenerationResult.Outcome.REFUSED;
            explanation = "The model declined to answer this request.";
        } else if (stopReason == BetaStopReason.MAX_TOKENS) {
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
