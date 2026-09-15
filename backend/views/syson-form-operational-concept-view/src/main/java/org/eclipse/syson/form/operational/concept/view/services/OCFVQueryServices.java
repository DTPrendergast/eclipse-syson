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

import org.eclipse.syson.sysml.Documentation;
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.OwningMembership;

/**
 * Reads the documentation body of an element, for the operational concept form.
 *
 * @author DTPrendergast
 */
public class OCFVQueryServices {

    /**
     * The element's documentation body, as authored.
     *
     * <p>
     * An element may own several {@code Documentation} elements; this reads the first, which is the
     * one the textual syntax produces for a single {@code doc} block and the one the editor writes
     * back to.
     * </p>
     *
     * @param element
     *            the element being edited
     * @return the body, or an empty string when the element has no documentation yet
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
        return element.getOwnedRelationship().stream()
                .filter(OwningMembership.class::isInstance)
                .map(OwningMembership.class::cast)
                .flatMap(owningMembership -> owningMembership.getOwnedRelatedElement().stream())
                .filter(org.eclipse.syson.sysml.MetadataUsage.class::isInstance)
                .map(org.eclipse.syson.sysml.MetadataUsage.class::cast)
                .anyMatch(metadataUsage -> metadataUsage.getMetadataDefinition() != null
                        && "OperationalConcept".equals(metadataUsage.getMetadataDefinition().getName()));
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
