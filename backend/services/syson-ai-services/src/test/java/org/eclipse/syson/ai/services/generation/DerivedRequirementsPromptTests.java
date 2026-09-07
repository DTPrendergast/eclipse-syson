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

import java.util.List;

import org.eclipse.syson.ai.services.scope.SerializedModelScope;
import org.junit.jupiter.api.Test;

/**
 * Tests of the goal 3 prompt.
 *
 * @author DTPrendergast
 */
class DerivedRequirementsPromptTests {

    private final DerivedRequirementsPromptFactory factory = new DerivedRequirementsPromptFactory();

    @Test
    void carriesTheInstructionAndTheSerializedModel() {
        var scope = new SerializedModelScope("requirement <REQ1> enduranceRequirement;", 1, false, List.of());

        var request = this.factory.create(scope, "Derive power subsystem requirements from REQ1.");

        assertThat(request.userPrompt()).contains("Derive power subsystem requirements from REQ1.");
        assertThat(request.userPrompt()).contains("requirement <REQ1> enduranceRequirement;");
    }

    @Test
    void asksForTextualSysmlBecauseTheParserIsWhatValidatesTheAnswer() {
        var request = this.factory.create(new SerializedModelScope("", 0, false, List.of()), "anything");

        assertThat(request.systemPrompt()).contains("Emit only textual SysML v2");
        assertThat(request.systemPrompt()).doesNotContain("JSON schema");
    }

    @Test
    void forbidsTheConstructsKnownToBeSilentlyLossy() {
        var request = this.factory.create(new SerializedModelScope("", 0, false, List.of()), "anything");

        // plan.md item 4: calc parses fine, then serializes back as action. A model that emits it
        // produces something that passes validation and changes meaning on the next export.
        assertThat(request.systemPrompt()).contains("Do not use `calc`");
    }

    @Test
    void tellsTheModelWhenItIsOnlySeeingPartOfTheModel() {
        var full = this.factory.create(new SerializedModelScope("part x;", 1, false, List.of()), "go");
        var truncated = this.factory.create(new SerializedModelScope("part x;", 1, true, List.of()), "go");

        assertThat(full.userPrompt()).doesNotContain("partial view");
        assertThat(truncated.userPrompt()).contains("partial view");
        assertThat(truncated.userPrompt()).contains("do not assume the rest is absent");
    }

    @Test
    void saysSoWhenNothingWasInScope() {
        var request = this.factory.create(new SerializedModelScope("", 0, false, List.of()), "go");

        assertThat(request.userPrompt()).contains("No model elements were in scope");
    }
}
