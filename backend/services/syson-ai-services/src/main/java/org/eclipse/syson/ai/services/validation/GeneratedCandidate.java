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
package org.eclipse.syson.ai.services.validation;

/**
 * One reviewable candidate recovered from a model's response.
 *
 * <p>
 * The fragment is not the model's own text: it is the element re-serialized after parsing. That
 * way what the reviewer reads, what gets stored on the proposal item, and what is eventually
 * applied are the same string, and any formatting the model invented has already been normalized
 * away by the parser.
 * </p>
 *
 * @param label
 *            how the candidate is shown in review
 * @param sysmlFragment
 *            the candidate as textual SysML v2
 *
 * @author DTPrendergast
 */
public record GeneratedCandidate(String label, String sysmlFragment) {
}
