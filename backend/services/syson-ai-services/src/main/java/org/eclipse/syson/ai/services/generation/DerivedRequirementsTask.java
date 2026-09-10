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
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.RequirementUsage;
import org.springframework.stereotype.Service;

/**
 * Proposes requirements derived from requirements and system structure (plan.md item 4, goal 3).
 *
 * @author DTPrendergast
 */
@Service
public class DerivedRequirementsTask implements IAiTask {

    public static final String KIND = "derived-requirements";

    private static final String SYSTEM_PROMPT = """
            You are assisting a systems engineer working in SysML v2. Your job is to propose \
            candidate derived requirements from requirements and system structure they give you.

            - Emit one `requirement` declaration per candidate, each with a `doc` comment stating \
              the requirement in a single testable sentence.
            - Do not restate a requirement you were given. Propose only what is genuinely derived \
              from it.
            """ + TaskPrompts.COMMON_RULES;

    private final TaskPrompts prompts;

    public DerivedRequirementsTask(TaskPrompts prompts) {
        this.prompts = Objects.requireNonNull(prompts);
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public Class<? extends Element> expectedKind() {
        return RequirementUsage.class;
    }

    @Override
    public GenerationRequest prompt(SerializedModelScope scope, String instruction) {
        return new GenerationRequest(SYSTEM_PROMPT, this.prompts.userPrompt(scope, instruction));
    }
}
