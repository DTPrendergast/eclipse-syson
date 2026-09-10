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
import org.eclipse.syson.sysml.Element;

/**
 * One thing the assistant knows how to do.
 *
 * <p>
 * plan.md item 4 lists seven goals and observes that they are not seven features: they share one
 * pipeline - scope, serialize, generate, validate, diff, apply - and differ only in wording,
 * scoping and output shape. This interface is that difference, and nothing else. Everything a task
 * does not define is handled identically for every task.
 * </p>
 *
 * @author DTPrendergast
 */
public interface IAiTask {

    /**
     * How this task is named on the wire and recorded on a proposal.
     *
     * @return a stable kebab-case identifier
     */
    String kind();

    /**
     * What every top-level element of a response must be.
     *
     * <p>
     * This is the gate that rejects prose. SysML v2 treats a bare name as an element declaration,
     * so English parses cleanly - the parse alone proves nothing, and this type check is what
     * actually decides whether a response is what was asked for.
     * </p>
     *
     * @return the metamodel type every candidate must be an instance of
     */
    Class<? extends Element> expectedKind();

    /**
     * Builds the prompt.
     *
     * @param scope
     *            the serialized model context
     * @param instruction
     *            what the engineer asked for, in their words
     * @return the request to send
     */
    GenerationRequest prompt(SerializedModelScope scope, String instruction);
}
