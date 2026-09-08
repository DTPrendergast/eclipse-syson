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
package org.eclipse.syson.ai.services.pipeline;

/**
 * One candidate as the review UI needs it.
 *
 * @param id
 *            the proposal item id, used when accepting or rejecting this one candidate
 * @param label
 *            how it is shown in review
 * @param sysmlFragment
 *            the textual SysML that will be applied verbatim if it is accepted, so a reviewer
 *            approves exactly what lands
 *
 * @author DTPrendergast
 */
public record ProposedItem(String id, String label, String sysmlFragment) {
}
