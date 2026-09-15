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

import java.util.List;

import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.components.representations.Message;
import org.eclipse.syson.ai.services.proposal.AiProposal;
import org.eclipse.syson.ai.services.proposal.AiProposalItem;
import org.eclipse.syson.ai.services.scope.SerializedModelScope;
import org.eclipse.syson.ai.services.validation.ValidationResult;
import org.eclipse.syson.sysml.Element;

/**
 * One thing the assistant knows how to do.
 *
 * <p>
 * plan.md item 4 observes that the seven goals are not seven features: they share one pipeline -
 * scope, serialize, generate, validate, review, apply - and differ only in wording, in what counts
 * as a valid answer, and in what applying one means. Those three things are this interface, and
 * nothing else is duplicated per task.
 * </p>
 *
 * <p>
 * Validation and application are on the task rather than in the pipeline because they genuinely
 * differ in kind, not just in parameters. A task that proposes requirements is answered with model
 * elements, checked by parsing and by type, and applied by importing them. A task that drafts a
 * narrative is answered with prose, for which parsing proves nothing - English parses cleanly as
 * SysML - and which is applied by replacing a documentation body rather than adding anything.
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
     * Builds the prompt.
     *
     * @param scope
     *            the serialized model context
     * @param instruction
     *            what the engineer asked for, in their words
     * @return the request to send
     */
    GenerationRequest prompt(SerializedModelScope scope, String instruction);

    /**
     * Decides whether a response is usable, and splits it into reviewable candidates.
     *
     * @param rawResponse
     *            exactly what the model returned
     * @return the candidates, plus anything that makes the response unusable
     */
    ValidationResult validate(String rawResponse);

    /**
     * Applies one candidate a reviewer has accepted.
     *
     * @param editingContext
     *            the editing context to write into
     * @param target
     *            the element the candidate applies to - the parent for a task that creates
     *            elements, the operational concept itself for one that rewrites its narrative
     * @param proposal
     *            the proposal the item belongs to, read for provenance
     * @param item
     *            the accepted candidate
     * @param messages
     *            where diagnostics are collected for the caller to surface
     * @return true when the model was changed and the item marked applied
     */
    boolean apply(IEMFEditingContext editingContext, Element target, AiProposal proposal, AiProposalItem item, List<Message> messages);
}
