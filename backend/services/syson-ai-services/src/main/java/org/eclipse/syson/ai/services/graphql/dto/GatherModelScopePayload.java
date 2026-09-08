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

import java.util.UUID;

import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.syson.ai.services.scope.SerializedModelScope;

/**
 * The serialized slice of the model, handed back off the editing context's thread.
 *
 * @param id
 *            the request id
 * @param scope
 *            the serialized scope; never null, possibly empty
 *
 * @author DTPrendergast
 */
public record GatherModelScopePayload(UUID id, SerializedModelScope scope) implements IPayload {
}
