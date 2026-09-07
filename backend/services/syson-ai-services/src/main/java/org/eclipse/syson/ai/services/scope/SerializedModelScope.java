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

/**
 * A {@link ModelScope} rendered as textual SysML v2, ready to be put in a prompt.
 *
 * @param text
 *            the rendered scope
 * @param elementCount
 *            how many elements actually made it into {@code text}
 * @param truncated
 *            true when rendering stopped early, either because the scope was already cut short by
 *            {@link ModelScopePolicy#maxElements} or because
 *            {@link ModelScopePolicy#maxCharacters} ran out mid-way. A caller that puts this in a
 *            prompt should say so in the prompt: a model shown a truncated model without being
 *            told will happily reason about the part it cannot see.
 * @param reports
 *            what the serializer complained about, one string per report. Empty is the normal case;
 *            anything here means some element did not render faithfully, which matters because the
 *            serializer fails silently rather than throwing (see plan.md item 4 on {@code calc}).
 *
 * @author DTPrendergast
 */
public record SerializedModelScope(String text, int elementCount, boolean truncated, List<String> reports) {

    public SerializedModelScope {
        reports = List.copyOf(reports);
    }
}
