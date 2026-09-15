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
package org.eclipse.syson.form.operational.concept.view.services;

import org.eclipse.syson.operational.concept.library.OperationalConceptNarrativeService;
import org.eclipse.syson.sysml.Documentation;
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.MetadataUsage;
import org.eclipse.syson.sysml.OwningMembership;

/**
 * Reads the operational concept narrative.
 *
 * <p>
 * <b>The narrative is documentation of the {@code OperationalConcept} annotation, not of the
 * package.</b> They are different things: a package's {@code doc} says what the package is, while
 * the narrative says who uses the system and how. Keeping them in one body forced one to stand in
 * for the other. Putting the narrative on the annotation gives each its own, and both still export
 * as ordinary {@code doc} blocks that any SysML v2 tool can read.
 * </p>
 *
 * <p>
 * The alternative of a string attribute on the annotation was tried and rejected: a quotation mark
 * inside a SysML string literal cannot be escaped - {@code narrative = "he said \"hi\""} stores as
 * {@code he said \} - and a narrative will certainly contain quotes.
 * </p>
 *
 * @author DTPrendergast
 */
public class OCFVQueryServices {

    /** Where the narrative actually lives is defined once, in the module that owns the concept. */
    private final OperationalConceptNarrativeService narrativeService = new OperationalConceptNarrativeService();

    /**
     * The narrative, read from the operational concept annotation.
     *
     * @param element
     *            the element being edited
     * @return the narrative, or an empty string when none has been written yet
     */
    public String getNarrative(Element element) {
        return this.narrativeService.getNarrative(element);
    }

    /**
     * The element's own documentation - what the package is, as distinct from the narrative.
     *
     * @param element
     *            the element being edited
     * @return the body, or an empty string when the element has no documentation
     */
    public String getDocumentationBody(Element element) {
        String body = "";
        Documentation documentation = this.firstDocumentation(element);
        if (documentation != null && documentation.getBody() != null) {
            body = documentation.getBody();
        }
        return body;
    }

    /**
     * The {@code OperationalConcept} annotation on the element, if it carries one.
     *
     * @param element
     *            the candidate element
     * @return the annotation, or null
     */
    public MetadataUsage operationalConceptAnnotation(Element element) {
        return this.narrativeService.findAnnotation(element);
    }

    /**
     * Whether this element is an operational concept.
     *
     * <p>
     * The marker is a metadata annotation rather than a type, so this looks for a
     * {@code MetadataUsage} whose definition is named {@code OperationalConcept} - the same test
     * the explorer's label delegate uses to pick its icon, kept consistent deliberately so the two
     * never disagree about what counts.
     * </p>
     *
     * @param element
     *            the candidate element
     * @return true when the element carries the OperationalConcept metadata
     */
    public boolean isOperationalConcept(Element element) {
        return this.operationalConceptAnnotation(element) != null;
    }

    private Documentation firstDocumentation(Element element) {
        return element.getOwnedRelationship().stream()
                .filter(OwningMembership.class::isInstance)
                .map(OwningMembership.class::cast)
                .flatMap(owningMembership -> owningMembership.getOwnedRelatedElement().stream())
                .filter(Documentation.class::isInstance)
                .map(Documentation.class::cast)
                .findFirst()
                .orElse(null);
    }
}
