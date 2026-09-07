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

import java.util.List;

import org.eclipse.syson.sysml.Element;

/**
 * The elements a generation will be given as context, already expanded and de-duplicated.
 *
 * @param seeds
 *            what the user actually selected, in selection order. Kept apart from the expansion so
 *            that a prompt can say which elements were asked about and which are only background.
 * @param elements
 *            every element in scope, seeds first, then the expansion in breadth-first order. A
 *            stable order matters: it makes a prompt reproducible and a diff of two scopes
 *            readable.
 * @param elementLimitReached
 *            true when expansion stopped because {@link ModelScopePolicy#maxElements} was hit, so
 *            the scope is a subset of what the policy would otherwise have reached
 *
 * @author DTPrendergast
 */
public record ModelScope(List<Element> seeds, List<Element> elements, boolean elementLimitReached) {

    public ModelScope {
        seeds = List.copyOf(seeds);
        elements = List.copyOf(elements);
    }

    public boolean isEmpty() {
        return this.elements.isEmpty();
    }
}
