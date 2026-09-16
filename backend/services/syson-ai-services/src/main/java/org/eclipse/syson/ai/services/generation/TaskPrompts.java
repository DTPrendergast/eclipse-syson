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

import org.eclipse.syson.ai.services.scope.SerializedModelScope;
import org.springframework.stereotype.Service;

/**
 * Assembles the parts of a prompt that are the same whatever the task.
 *
 * <p>
 * The rules collected here are not stylistic. Each one exists because of something measured
 * against the real parser and recorded in plan.md: textual SysML because the parser is what
 * validates a response; no Markdown fences because the validator has to strip them; no
 * {@code calc} because it parses fine and then serializes back as {@code action}, changing meaning
 * on the next export.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
public class TaskPrompts {

    /** Appended to every task's own instructions. */
    public static final String COMMON_RULES = """

            Rules that apply whatever you are asked for:
            - Emit only textual SysML v2. No prose, no JSON, no Markdown fences.
            - Every top-level declaration you emit must be of the kind asked for above. A response \
            that mixes in commentary is rejected whole, so put explanation inside `doc` comments \
            rather than outside the model.
            - Derive from what you are shown. Do not invent components, interfaces or numeric \
            budgets that are not present or clearly implied.
            - Do not use `calc`: it does not survive a round trip through this tool's serializer. \
            Use `action name : ReturnType` instead.
            - If the context is too thin to produce anything defensible, emit nothing rather than \
            guessing.

            Everything you produce is reviewed by an engineer before it enters the model, and each \
            candidate is accepted or rejected on its own. Precision is worth more than volume.
            """;

    /**
     * Removes Markdown code fences from a response.
     *
     * <p>
     * Shared by the prose tasks, whose answers are not parsed and so have no other chance to lose
     * a fence the model added despite being told not to.
     * </p>
     *
     * @param text
     *            the raw response
     * @return the response without surrounding fences
     */
    public String stripCodeFences(String text) {
        String result = text.strip();
        if (result.startsWith("```")) {
            int firstLineEnd = result.indexOf('\n');
            if (firstLineEnd >= 0) {
                result = result.substring(firstLineEnd + 1);
            }
            if (result.stripTrailing().endsWith("```")) {
                result = result.stripTrailing();
                result = result.substring(0, result.length() - 3);
            }
            result = result.strip();
        }
        return result;
    }

    /**
     * Wraps the engineer's instruction and the model context in the shape every task uses.
     *
     * @param scope
     *            the serialized model context
     * @param instruction
     *            what the engineer asked for
     * @return the user half of the prompt
     */
    public String userPrompt(SerializedModelScope scope, String instruction) {
        var prompt = new StringBuilder();
        prompt.append("## What I need").append(System.lineSeparator());
        prompt.append(instruction).append(System.lineSeparator()).append(System.lineSeparator());
        prompt.append("## The model").append(System.lineSeparator());

        if (scope.truncated()) {
            // A model shown a partial model without being told will reason confidently about the
            // part it cannot see.
            prompt.append("Note: this is a partial view of a larger model - it was truncated to fit. ")
                    .append("Work only from what is shown, and do not assume the rest is absent.")
                    .append(System.lineSeparator());
        }
        if (scope.elementCount() == 0) {
            prompt.append("(No model elements were in scope.)").append(System.lineSeparator());
        }

        prompt.append(System.lineSeparator()).append(scope.text());
        return prompt.toString();
    }
}
