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

import java.util.List;
import java.util.UUID;

import org.eclipse.sirius.components.core.api.IInput;

/**
 * Internal request to read and serialize a slice of the model.
 *
 * <p>
 * Deliberately not exposed in the GraphQL schema. It exists so that the model reading - the only
 * part of a generation that must happen on the editing context's own thread - can be dispatched
 * on its own and finish quickly, leaving the slow model call to run elsewhere.
 * </p>
 *
 * @param id
 *            the request id
 * @param editingContextId
 *            the project to read from
 * @param elementIds
 *            the selected elements to expand from
 *
 * @author DTPrendergast
 */
public record GatherModelScopeInput(UUID id, String editingContextId, List<String> elementIds) implements IInput {
}
