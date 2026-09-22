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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.Objects;

import org.eclipse.sirius.components.emf.services.api.IEMFEditingContext;
import org.eclipse.sirius.components.representations.Message;

import org.eclipse.syson.ai.services.pipeline.ProposalApplyService;
import org.eclipse.syson.ai.services.proposal.AiProposal;
import org.eclipse.syson.ai.services.proposal.AiProposalItem;
import org.eclipse.syson.ai.services.scope.SerializedModelScope;
import org.eclipse.syson.ai.services.validation.GeneratedSysmlValidator;
import org.eclipse.syson.ai.services.validation.ValidationResult;
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.operational.concept.library.OperationalConceptNarrativeService;
import org.eclipse.syson.operational.concept.library.UseCasePackageService;
import org.eclipse.syson.sysml.Package;
import org.eclipse.syson.sysml.UseCaseUsage;
import org.springframework.stereotype.Service;

/**
 * Drafts use cases from an operational concept (plan.md item 4, goal 1 territory).
 *
 * <p>
 * Aimed at the operational-concept packages item 2 introduced: a narrative describing who uses the
 * system and how, from which concrete use cases can be drawn. The narrative reaches the model
 * because the scope serializer emits {@code doc} bodies inline.
 * </p>
 *
 * <p>
 * {@code use case} usages only, not {@code use case def}. Both parse cleanly - checked - but an
 * operational concept describes what people actually do rather than defining reusable types, and
 * allowing either would mean a reviewer accepting candidates of two different kinds from one run.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
public class DraftUseCasesTask implements IAiTask {

    public static final String KIND = "draft-use-cases";

    /**
     * The include form that silently declares instead of referencing. Proven against the real
     * parser in {@code UseCaseBodyImportTests}: it creates a nested use case sharing the name,
     * binds nothing, and raises no message.
     */
    private static final Pattern INCLUDE_DECLARATION_FORM = Pattern.compile("\\binclude\\s+use\\s+case\\s+");

    private static final String SYSTEM_PROMPT = """
            You are assisting a systems engineer working in SysML v2. Your job is to write use \
            cases from an operational concept narrative: a document describing who uses a system \
            and how they intend to use it.

            The narrative has a section describing operational employment, with one `###` heading \
            per use case. Unless the engineer asks for something narrower, write one use case for \
            every such heading. If they name a single use case or section, write only that one.

            Emit each as a complete `use case` declaration. Use `use case`, never `use case def`. \
            Name each after the goal being achieved, not the mechanism - `restorePower`, not \
            `pressRestoreButton`. Inside each declaration, include what the narrative supports:

            - a `doc` comment saying, in a few sentences, who is doing what and to what end
            - `subject` naming what the use case is about
            - `actor` for each party who performs or is affected by it
            - `action` steps for what happens, in order, using `first` and `then` where the \
            sequence matters
            - `include` for a step that is itself one of the other use cases

            Reuse rather than repeat. When a step is described in detail under one heading and \
            merely referred to under others, it is one use case that the others include.

            Write an include as `include thatUseCase;` - the bare name. Never write \
            `include use case thatUseCase;`: that declares a new nested use case that happens to \
            share the name, references nothing, and the parser will not warn you.

            An include can only reach a use case you write in this same reply. If the engineer \
            asked for one use case and it depends on others, write the ones it includes as well \
            rather than referring to use cases that are not in front of you.

            Stay inside the narrative. The actors, systems and steps it describes are there to be \
            used; do not invent numeric values, timings or interfaces that appear nowhere. Where \
            the narrative implies an actor or a step without saying so outright, a use case is \
            justified; where it says nothing, do not invent one.
            """ + TaskPrompts.COMMON_RULES;

    private final TaskPrompts prompts;

    private final GeneratedSysmlValidator validator;

    private final ProposalApplyService applyService;

    private final UseCasePackageService useCasePackageService;

    private final OperationalConceptNarrativeService narrativeService;

    public DraftUseCasesTask(TaskPrompts prompts, GeneratedSysmlValidator validator, ProposalApplyService applyService,
            UseCasePackageService useCasePackageService, OperationalConceptNarrativeService narrativeService) {
        this.prompts = Objects.requireNonNull(prompts);
        this.validator = Objects.requireNonNull(validator);
        this.applyService = Objects.requireNonNull(applyService);
        this.useCasePackageService = Objects.requireNonNull(useCasePackageService);
        this.narrativeService = Objects.requireNonNull(narrativeService);
    }

    @Override
    public String kind() {
        return KIND;
    }

    /**
     * The gate that rejects prose. SysML v2 treats a bare name as an element declaration, so
     * English parses cleanly - the parse proves nothing, and this type check is what decides
     * whether a response is what was asked for.
     */
    @Override
    public ValidationResult validate(String rawResponse) {
        String text = "";
        if (rawResponse != null) {
            text = rawResponse;
        }
        String repaired = INCLUDE_DECLARATION_FORM.matcher(text).replaceAll("include ");

        ValidationResult result = this.validator.validate(repaired, UseCaseUsage.class);
        if (!repaired.equals(text)) {
            // Said out loud rather than fixed quietly: the reviewer is being shown SysML that is
            // not quite what the model returned, and the difference is the one between a use case
            // that references another and one that shadows it.
            var warnings = new ArrayList<>(result.warnings());
            warnings.add("Rewrote `include use case X` to `include X`. The first form declares a new nested use case instead of referencing the existing one, and the parser reports nothing.");
            result = new ValidationResult(result.candidates(), result.errors(), warnings, result.fencesStripped());
        }
        return result;
    }

    /**
     * Files the use case in the concept's "Use Cases" package, creating that package the first
     * time. Anywhere else, the element lands where the assistant was opened - the redirect is a
     * convention about operational concepts, not a rule about use cases in general.
     */
    @Override
    public boolean apply(IEMFEditingContext editingContext, Element target, AiProposal proposal, AiProposalItem item, List<Message> messages) {
        Element parent = target;
        if (this.narrativeService.findAnnotation(target) != null) {
            Package useCases = this.useCasePackageService.findOrCreate(target);
            if (useCases != null) {
                parent = useCases;
            }
        }
        return this.applyService.apply(editingContext, parent, proposal, item, messages);
    }

    @Override
    public GenerationRequest prompt(SerializedModelScope scope, String instruction) {
        return new GenerationRequest(SYSTEM_PROMPT, this.prompts.userPrompt(scope, instruction));
    }
}
