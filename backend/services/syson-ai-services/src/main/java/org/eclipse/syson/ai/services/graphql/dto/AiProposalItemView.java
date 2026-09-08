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
package org.eclipse.syson.ai.services.graphql.dto;

/**
 * One stored candidate as the review UI reads it back.
 *
 * @param id
 *            the item id, used when applying this one candidate
 * @param label
 *            how it is shown in review
 * @param sysmlFragment
 *            what will be added verbatim if it is accepted
 * @param status
 *            PENDING, ACCEPTED or APPLIED - so a resumed review does not offer to apply something
 *            that has already been applied
 *
 * @author DTPrendergast
 */
public record AiProposalItemView(String id, String label, String sysmlFragment, String status) {
}
