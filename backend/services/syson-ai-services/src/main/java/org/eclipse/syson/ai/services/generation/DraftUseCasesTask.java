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
import org.eclipse.syson.sysml.UseCaseUsage;
import org.springframework.stereotype.Service;

/**
 * Drafts use cases from an operational concept (plan.md item 4, goal 1 territory).
 *
 * <p>
 * Aimed at the operational-concept packages item 2 introduced: a narrative describing who uses the
 * system and how, from which concrete use cases can be drawn. The narrative reaches the model
 * because the scope serializer emits {@code doc} bodies inline.
 * </p>
 *
 * <p>
 * {@code use case} usages only, not {@code use case def}. Both parse cleanly - checked - but an
 * operational concept describes what people actually do rather than defining reusable types, and
 * allowing either would mean a reviewer accepting candidates of two different kinds from one run.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
public class DraftUseCasesTask implements IAiTask {

    public static final String KIND = "draft-use-cases";

    private static final String SYSTEM_PROMPT = """
            You are assisting a systems engineer working in SysML v2. Your job is to draft \
            candidate use cases from an operational concept: a narrative describing who uses a \
            system and how they intend to use it.

            - Emit one `use case` declaration per candidate. Use `use case`, never `use case def`.
            - Give each a `doc` comment saying, in one or two sentences, who is doing what and to \
              what end.
            - Name each after the goal being achieved, not the mechanism - `restorePower`, not \
              `pressRestoreButton`.
            - Cover what the narrative actually describes. Where it implies an actor or a step \
              without saying so outright, a use case is justified; where it says nothing, do not \
              invent one.
            """ + TaskPrompts.COMMON_RULES;

    private final TaskPrompts prompts;

    public DraftUseCasesTask(TaskPrompts prompts) {
        this.prompts = Objects.requireNonNull(prompts);
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public Class<? extends Element> expectedKind() {
        return UseCaseUsage.class;
    }

    @Override
    public GenerationRequest prompt(SerializedModelScope scope, String instruction) {
        return new GenerationRequest(SYSTEM_PROMPT, this.prompts.userPrompt(scope, instruction));
    }
}
