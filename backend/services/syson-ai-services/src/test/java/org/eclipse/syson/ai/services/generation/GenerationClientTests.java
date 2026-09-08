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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests of the configuration gate in front of the Anthropic client.
 *
 * <p>
 * These never reach the network. The one behaviour worth pinning down without credentials is that
 * an unconfigured assistant degrades to a reported outcome rather than an exception or a silent
 * empty answer, because that is what lets the rest of the tool be built and run without a key.
 * </p>
 *
 * @author DTPrendergast
 */
class GenerationClientTests {

    @Test
    void defaultsToOpus5AndAResponseCeilingThatWillNotTruncateASetOfRequirements() {
        var properties = new SysONAiProperties();

        assertThat(properties.getModel()).isEqualTo("claude-opus-5");
        assertThat(properties.getMaxTokens()).isEqualTo(16000);
    }

    @Test
    void isNotConfiguredUntilAKeyIsSet() {
        var properties = new SysONAiProperties();

        assertThat(properties.isConfigured()).isFalse();

        properties.setApiKey("   ");
        assertThat(properties.isConfigured()).as("whitespace is not a key").isFalse();

        properties.setApiKey("sk-ant-test");
        assertThat(properties.isConfigured()).isTrue();
    }

    @Test
    void sendsTheRefusalFallbackSoARefusedRequestIsRetriedRatherThanLost() {
        var properties = new SysONAiProperties();
        properties.setApiKey("sk-ant-test");
        var client = new AnthropicGenerationClient(properties);

        var params = client.paramsFor(new GenerationRequest("system", "user"));

        // The SDK at this version has no typed binding for fallbacks, so this goes on as a raw
        // body property. That makes the wire shape untypechecked, which is exactly why it is
        // asserted here rather than assumed - and it can be asserted without an API key.
        assertThat(params.betas()).isPresent();
        assertThat(params.betas().get()).extracting(beta -> beta.toString()).contains("server-side-fallback-2026-06-01");
        assertThat(params._additionalBodyProperties()).containsKey("fallbacks");
        assertThat(params._additionalBodyProperties().get("fallbacks").toString()).contains("claude-opus-4-8");
    }

    @Test
    void omitsTheFallbackEntirelyWhenItIsTurnedOff() {
        var properties = new SysONAiProperties();
        properties.setApiKey("sk-ant-test");
        properties.setFallbackModel("");
        var client = new AnthropicGenerationClient(properties);

        var params = client.paramsFor(new GenerationRequest("system", "user"));

        assertThat(params._additionalBodyProperties()).doesNotContainKey("fallbacks");
        assertThat(params.betas()).isEmpty();
    }

    @Test
    void withoutAKeyItReportsNotConfiguredRatherThanThrowingOrReturningAnEmptyAnswer() {
        var client = new AnthropicGenerationClient(new SysONAiProperties());

        var result = client.generate(new GenerationRequest("system", "user"));

        assertThat(result.outcome()).isEqualTo(GenerationResult.Outcome.NOT_CONFIGURED);
        assertThat(result.isUsable()).isFalse();
        assertThat(result.explanationIfAny()).isPresent();
        assertThat(result.explanationIfAny().get()).contains("ANTHROPIC_API_KEY");
    }
}
