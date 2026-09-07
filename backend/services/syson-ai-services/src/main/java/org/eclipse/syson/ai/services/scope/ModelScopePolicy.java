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
package org.eclipse.syson.ai.services.scope;

/**
 * How far a scope selection is expanded, and how much of the result may be sent to a model.
 *
 * <p>
 * plan.md item 4 lists scope control as a non-negotiable: sending an entire large model as context
 * is slow and expensive, and the cost is paid per generation. Every limit here exists so that the
 * caller decides the ceiling rather than discovering it on an invoice.
 * </p>
 *
 * @param ownedDepth
 *            how many levels of owned elements are pulled in below each seed. Depth 0 is the seed
 *            alone; depth 1 adds its immediate children.
 * @param relatedDepth
 *            how many hops of non-containment references are followed from the elements already in
 *            scope, which is what brings in a requirement's subject or a part's type. Kept separate
 *            from {@code ownedDepth} because it fans out far faster.
 * @param maxElements
 *            the most elements the scope may hold, counted after de-duplication.
 * @param maxCharacters
 *            the most characters the serialized form may hold. A character budget rather than a
 *            token budget: tokenization is model-specific, and this layer does not know which model
 *            the caller will use.
 *
 * @author DTPrendergast
 */
public record ModelScopePolicy(int ownedDepth, int relatedDepth, int maxElements, int maxCharacters) {

    public ModelScopePolicy {
        if (ownedDepth < 0 || relatedDepth < 0) {
            throw new IllegalArgumentException("Depths must not be negative");
        }
        if (maxElements < 1 || maxCharacters < 1) {
            throw new IllegalArgumentException("Budgets must be strictly positive");
        }
    }

    /**
     * A starting point that keeps a typical selection well inside a single request.
     *
     * <p>
     * One hop of related elements, because two hops on a typed part usage tends to drag in a chain
     * of definitions that the model does not need in order to derive requirements.
     * </p>
     *
     * @return the default policy
     */
    public static ModelScopePolicy defaultPolicy() {
        return new ModelScopePolicy(3, 1, 200, 60000);
    }
}
