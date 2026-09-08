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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.sirius.components.core.api.IIdentityService;
import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.components.representations.Message;
import org.eclipse.sirius.components.representations.MessageLevel;
import org.eclipse.syson.ai.services.proposal.AiProposal;
import org.eclipse.syson.ai.services.proposal.AiProposalItem;
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.services.DeleteService;
import org.eclipse.syson.sysml.services.api.ISysMLTextImporter;
import org.springframework.stereotype.Service;

/**
 * Applies one accepted candidate to the model, stamped with where it came from.
 *
 * <p>
 * This is the only code in the assistant that writes to a user's model, and it is deliberately
 * small and synchronous: it parses a fragment that has already been validated and adds it to a
 * parent element. No model call happens here, so it is safe to run on the platform's
 * single-threaded per-project executor.
 * </p>
 *
 * <p>
 * <b>An element never stays in the model without its provenance.</b> Attaching metadata takes a
 * second import - the annotation has to be imported <em>into</em> the created element, which is
 * how every other metadata edit in this application works - so if that second step fails the first
 * one is rolled back and nothing lands at all. The alternative, leaving a generated element in the
 * model unstamped, is precisely the failure this library exists to prevent.
 * </p>
 *
 * <p>
 * The annotation is written {@code @AiProvenanceLibrary::Generated { ... }} and imported with the
 * new element as its parent. Three forms were tried against the real parser and only this one both
 * attaches and carries values: written at the same level as the declaration the metadata becomes a
 * sibling rather than an annotation; written as prefix metadata ({@code #Name}) it attaches but
 * carries no values; and written unqualified the type silently fails to resolve. All three parse
 * without errors.
 * </p>
 *
 * <p>
 * The prompt is not stamped onto the element. Free text would have to be embedded as a SysML
 * string literal, and a quotation mark inside one cannot be escaped (plan.md item 3), so the
 * annotation carries the proposal id instead - a pointer to the row holding the prompt, the model,
 * the author, and the other candidates from the same run.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
public class ProposalApplyService {

    private final ISysMLTextImporter textImporter;

    private final IIdentityService identityService;

    /** Constructed rather than injected: DeleteService is a plain stateless helper, not a bean, and this is how every other service in this fork obtains one. */
    private final DeleteService deleteService = new DeleteService();

    public ProposalApplyService(ISysMLTextImporter textImporter, IIdentityService identityService) {
        this.textImporter = Objects.requireNonNull(textImporter);
        this.identityService = Objects.requireNonNull(identityService);
    }

    /**
     * Applies one item.
     *
     * @param editingContext
     *            the editing context to write into
     * @param parent
     *            the element the new element is added under
     * @param proposal
     *            the proposal the item belongs to, read for provenance
     * @param item
     *            the item to apply; it must have been accepted by a reviewer
     * @param messages
     *            where import diagnostics are collected for the caller to surface
     * @return true when the element was created, stamped, and the item marked applied
     */
    public boolean apply(IEMFEditingContext editingContext, Element parent, AiProposal proposal, AiProposalItem item, List<Message> messages) {
        Objects.requireNonNull(editingContext);
        Objects.requireNonNull(parent);
        Objects.requireNonNull(proposal);
        Objects.requireNonNull(item);

        var elementMessages = new ArrayList<Message>();
        List<Element> created = this.textImporter.importSysMLText(editingContext, parent, item.getSysmlFragment(), elementMessages);
        messages.addAll(elementMessages);

        boolean applied = false;
        if (!this.hasError(elementMessages) && !created.isEmpty()) {
            applied = this.stamp(editingContext, proposal, item, created.get(0), messages);
        }
        return applied;
    }

    /**
     * Attaches provenance to a freshly created element, removing the element again if that fails.
     */
    private boolean stamp(IEMFEditingContext editingContext, AiProposal proposal, AiProposalItem item, Element element, List<Message> messages) {
        var provenanceMessages = new ArrayList<Message>();
        this.textImporter.importSysMLText(editingContext, element, this.provenanceAnnotation(proposal), provenanceMessages);
        messages.addAll(provenanceMessages);

        boolean stamped = !this.hasError(provenanceMessages);
        if (!stamped) {
            // Rolled back rather than left in place: an unstamped generated element is
            // indistinguishable from hand-authored content, which is the whole thing this
            // is here to prevent.
            this.deleteService.deleteFromModel(element);
            messages.add(new Message("Could not record where this element came from, so it was not added.", MessageLevel.ERROR));
        } else {
            item.applied(this.identityService.getId(element));
        }
        return stamped;
    }

    private boolean hasError(List<Message> messages) {
        return messages.stream().anyMatch(message -> message.level() == MessageLevel.ERROR);
    }

    /**
     * The provenance annotation, imported with the generated element as its parent.
     */
    private String provenanceAnnotation(AiProposal proposal) {
        var text = new StringBuilder();
        text.append("@AiProvenanceLibrary::Generated {")
                .append(" generatedBy = \"").append(this.sanitize(proposal.getModelId())).append("\";")
                .append(" generatedAt = \"").append(Instant.now().toString()).append("\";")
                .append(" proposalId = \"").append(proposal.getId()).append("\";")
                .append(" }");
        return text.toString();
    }

    /**
     * Strips what cannot be represented in a SysML string literal.
     *
     * <p>
     * A quotation mark inside a literal cannot be escaped, so one arriving here would produce a
     * fragment that parses into something other than what was intended. These values are ids and
     * model names rather than free text, so removing the characters is safe; the authoritative
     * copy is in the proposal row either way.
     * </p>
     */
    private String sanitize(String value) {
        String result = "";
        if (value != null) {
            result = value.replace("\"", "").replace("\\", "");
        }
        return result;
    }
}
