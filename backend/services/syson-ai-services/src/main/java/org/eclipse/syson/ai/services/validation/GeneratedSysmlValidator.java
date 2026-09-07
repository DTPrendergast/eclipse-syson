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

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.syson.sysml.ASTTransformer;
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.Namespace;
import org.eclipse.syson.sysml.SysmlToAst;
import org.eclipse.syson.sysml.metamodel.services.textual.SysMLElementSerializer;
import org.eclipse.syson.sysml.metamodel.services.textual.SysMLSerializingOptions;
import org.eclipse.syson.sysml.metamodel.services.textual.utils.FileNameDeresolver;
import org.eclipse.syson.sysml.metamodel.services.textual.utils.Severity;
import org.eclipse.syson.sysml.metamodel.services.textual.utils.Status;
import org.springframework.stereotype.Service;

/**
 * Checks a model's response by parsing it, before any of it is offered for review.
 *
 * <p>
 * The parser is the validation. Rather than pattern-matching the response or trusting it, this
 * runs it through the same syside pipeline every textual edit in the application goes through: if
 * the parser will not read it, it is not SysML v2 and there is nothing to review.
 * </p>
 *
 * <p>
 * <b>This deliberately does not use {@code ISysMLTextImporter}.</b> That interface parses
 * <em>and integrates</em>, adding the result into a parent element in a live editing context - it
 * is the write path. Validation happens before a human has approved anything, so it parses into a
 * throwaway {@link ResourceSet} that the project never sees. The importer is the right tool at
 * apply time, once individual candidates have been accepted.
 * </p>
 *
 * <p>
 * <b>Parsing alone is not enough, and that is not obvious.</b> SysML v2 accepts a bare name as an
 * element declaration, so ordinary English prose parses cleanly: "Sure! Here are some requirements"
 * yields nine well-formed elements named {@code Sure}, {@code Here}, {@code are} and so on, with
 * zero parse errors. A chatty model would sail through a parse-only check and put nine pieces of
 * junk in front of a reviewer. So every candidate must also be of the kind the caller asked for -
 * that check, not the parse, is what actually rejects prose.
 * </p>
 *
 * <p>
 * The consequence worth knowing: because validation runs against a standalone resource set,
 * references from the generated text into the project or the standard libraries do not resolve
 * here. This checks that the response is well-formed SysML, not that every name in it exists.
 * Name resolution is the importer's job at apply time.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
public class GeneratedSysmlValidator {

    private static final SysMLSerializingOptions OPTIONS = new SysMLSerializingOptions.Builder()
            .lineSeparator(System.lineSeparator())
            .nameDeresolver(new FileNameDeresolver())
            .indentation("\t")
            .needEscapeCharacter(true)
            .build();

    private final SysmlToAst sysmlToAst;

    public GeneratedSysmlValidator(SysmlToAst sysmlToAst) {
        this.sysmlToAst = Objects.requireNonNull(sysmlToAst);
    }

    /**
     * Validates one response.
     *
     * @param rawResponse
     *            exactly what the model returned
     * @param expectedKind
     *            the metamodel type every top-level element must be an instance of, supplied by
     *            the per-goal adapter - {@code RequirementUsage} when deriving requirements. This
     *            is what rejects prose; see the note on this class.
     * @return what was found, including the individually reviewable candidates
     */
    public ValidationResult validate(String rawResponse, Class<? extends Element> expectedKind) {
        Objects.requireNonNull(expectedKind);
        var errors = new ArrayList<String>();
        var warnings = new ArrayList<String>();

        String text = rawResponse;
        if (text == null) {
            text = "";
        }
        String stripped = this.stripCodeFences(text);
        boolean fencesStripped = !stripped.equals(text);

        List<GeneratedCandidate> candidates = List.of();
        if (!stripped.isBlank()) {
            // A blank response is not an error: the prompt tells the model to emit nothing rather
            // than guess when the context is too thin.
            candidates = this.parse(stripped, expectedKind, errors, warnings);
        }

        return new ValidationResult(candidates, errors, warnings, fencesStripped);
    }

    private List<GeneratedCandidate> parse(String text, Class<? extends Element> expectedKind, List<String> errors, List<String> warnings) {
        var parsingResult = this.sysmlToAst.convert(new ByteArrayInputStream(text.getBytes()), ".sysml");
        this.sortReports(parsingResult.reports(), errors, warnings);

        List<GeneratedCandidate> candidates = List.of();
        if (parsingResult.ast().isEmpty()) {
            errors.add("The response could not be parsed as SysML v2.");
        } else {
            var transformer = new ASTTransformer();
            ResourceSet resourceSet = new ResourceSetImpl();
            Resource resource = transformer.convertResource(parsingResult.ast().get(), resourceSet);
            transformer.getTransformationMessages().stream()
                    .map(message -> message.body())
                    .forEach(warnings::add);
            candidates = this.candidatesOf(resource, expectedKind, errors, warnings);
        }
        return candidates;
    }

    /**
     * The elements a reviewer will accept or reject one at a time: the top-level declarations of
     * the parsed text, not every element in its tree.
     */
    private List<GeneratedCandidate> candidatesOf(Resource resource, Class<? extends Element> expectedKind, List<String> errors, List<String> warnings) {
        var candidates = new ArrayList<GeneratedCandidate>();
        var reports = new ArrayList<Status>();
        var serializer = new SysMLElementSerializer(OPTIONS, reports::add);

        for (Element element : this.topLevelElements(resource)) {
            if (!expectedKind.isInstance(element)) {
                // Almost always prose that happened to parse. Reported as an error rather than
                // filtered out silently, because a response that is partly prose is a response
                // that should be regenerated, not quietly trimmed.
                errors.add("Expected only " + expectedKind.getSimpleName() + " declarations, but the response contained "
                        + element.eClass().getName() + " '" + this.labelOf(element) + "'.");
                continue;
            }
            String fragment = serializer.doSwitch(element);
            if (fragment == null || fragment.isBlank()) {
                continue;
            }
            candidates.add(new GeneratedCandidate(this.labelOf(element), fragment));
        }

        // A serializer report here means the element does not survive being written back out, so
        // the reviewer would be approving something different from what gets applied.
        reports.stream().map(Status::toString).forEach(warnings::add);
        return candidates;
    }

    private List<Element> topLevelElements(Resource resource) {
        var elements = new ArrayList<Element>();
        for (var root : resource.getContents()) {
            if (root instanceof Namespace namespace) {
                elements.addAll(namespace.getOwnedElement());
            } else if (root instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }

    private String labelOf(Element element) {
        String label = element.getElementId();
        String declaredName = element.getDeclaredName();
        if (declaredName != null && !declaredName.isBlank()) {
            label = declaredName;
        }
        return label;
    }

    private void sortReports(List<Status> reports, List<String> errors, List<String> warnings) {
        for (Status status : reports) {
            if (status.severity() == Severity.ERROR) {
                errors.add(status.message());
            } else {
                warnings.add(status.message());
            }
        }
    }

    /**
     * Removes Markdown code fences.
     *
     * <p>
     * The prompt forbids them and models still emit them often enough that failing the whole
     * response over three backticks would be the wrong trade. The fact that it happened is
     * reported rather than swallowed.
     * </p>
     */
    private String stripCodeFences(String text) {
        String result = text.strip();
        if (result.startsWith("```")) {
            int firstLineEnd = result.indexOf('\n');
            if (firstLineEnd >= 0) {
                result = result.substring(firstLineEnd + 1);
            }
            if (result.stripTrailing().endsWith("```")) {
                result = result.stripTrailing();
                result = result.substring(0, result.length() - 3);
            }
            result = result.strip();
        }
        return result;
    }
}
