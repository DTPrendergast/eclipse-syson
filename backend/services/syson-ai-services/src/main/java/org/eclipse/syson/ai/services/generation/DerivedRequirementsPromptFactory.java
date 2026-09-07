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

import org.eclipse.syson.ai.services.scope.SerializedModelScope;
import org.springframework.stereotype.Service;

/**
 * Builds the prompt for goal 3, deriving requirements.
 *
 * <p>
 * This is the first of what plan.md calls the thin per-goal adapters: the pipeline around it -
 * scope, serialize, generate, validate, diff, apply - is the same for every goal, and what differs
 * is the wording, the scoping strategy and the shape of the output. Keeping the wording here, and
 * out of the client, is what makes that true.
 * </p>
 *
 * <p>
 * Two things the system prompt is deliberate about. It asks for textual SysML v2 rather than JSON
 * or prose, because the response is validated by re-parsing it through the same importer every
 * other textual edit goes through - the parser is the check, so the model has to produce something
 * the parser can read. And it forbids the constructs plan.md recorded as silently lossy: {@code calc}
 * serializes back as {@code action}, so a model that emits it produces something that survives the
 * parser but changes meaning on the next export.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
public class DerivedRequirementsPromptFactory {

    private static final String SYSTEM_PROMPT = """
            You are assisting a systems engineer working in SysML v2. Your job is to propose \
            candidate derived requirements from requirements and system structure they give you.

            Rules for your output:
            - Emit only textual SysML v2. No prose, no JSON, no Markdown fences.
            - Emit one `requirement` declaration per candidate, each with a `doc` comment stating \
              the requirement in a single testable sentence.
            - Derive from what you are shown. Do not invent components, interfaces or numeric \
              budgets that are not present or clearly implied.
            - Do not use `calc`: it does not survive a round trip through this tool's serializer. \
              Use `action name : ReturnType` instead.
            - Do not restate a requirement you were given. Propose only what is genuinely derived \
              from it.
            - If the context is too thin to derive anything defensible, emit nothing rather than \
              guessing.

            Everything you produce is reviewed by an engineer before it enters the model, and \
            each candidate is accepted or rejected on its own. Precision is worth more than volume.
            """;

    /**
     * Assembles the prompt.
     *
     * @param scope
     *            the serialized model context
     * @param instruction
     *            what the engineer asked for, in their words
     * @return the request to send
     */
    public GenerationRequest create(SerializedModelScope scope, String instruction) {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(instruction);

        var userPrompt = new StringBuilder();
        userPrompt.append("## What I need").append(System.lineSeparator());
        userPrompt.append(instruction).append(System.lineSeparator()).append(System.lineSeparator());
        userPrompt.append("## The model").append(System.lineSeparator());

        if (scope.truncated()) {
            // Saying so matters: a model shown a partial model without being told will reason
            // confidently about the part it cannot see.
            userPrompt.append("Note: this is a partial view of a larger model - it was truncated to fit. ")
                    .append("Derive only from what is shown, and do not assume the rest is absent.")
                    .append(System.lineSeparator());
        }
        if (scope.elementCount() == 0) {
            userPrompt.append("(No model elements were in scope.)").append(System.lineSeparator());
        }

        userPrompt.append(System.lineSeparator()).append(scope.text());

        return new GenerationRequest(SYSTEM_PROMPT, userPrompt.toString());
    }
}
