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
 * Asks the assistant to run one task over a selection.
 *
 * <p>
 * Note what is <em>not</em> here: who is asking. Provenance must not be self-reported by the
 * client - an author field on this input would let a caller stamp anyone's name on generated
 * content, which is worse than recording nothing, because it looks authoritative. See
 * {@code MutationGenerateAiProposalDataFetcher} for how the author is resolved and what
 * still needs wiring.
 * </p>
 *
 * @param id
 *            the mutation id
 * @param editingContextId
 *            the project being worked on
 * @param elementIds
 *            what the engineer selected
 * @param task
 *            which assistant task to run, e.g. derived-requirements or draft-use-cases. Explicit
 *            rather than inferred: the task decides what kind of element every candidate must be,
 *            and guessing it would put candidates of an unexpected kind in front of a reviewer
 * @param instruction
 *            what they asked for
 *
 * @author DTPrendergast
 */
public record GenerateAiProposalInput(UUID id, String editingContextId, List<String> elementIds, String task, String instruction) implements IInput {
}
