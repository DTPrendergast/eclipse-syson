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

import org.eclipse.syson.sysml.Documentation;
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.OwningMembership;
import org.eclipse.syson.sysml.SysmlFactory;
import org.springframework.stereotype.Service;

/**
 * Reads and replaces an element's own documentation body.
 *
 * <p>
 * Distinct from {@code OperationalConceptNarrativeService}, which deliberately writes to the
 * documentation of the {@code OperationalConcept} annotation rather than of the element. A use case
 * has no such annotation and no competing body: its {@code doc} is simply what the use case is, so
 * this writes there.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
public class ElementDocumentationService {

    /**
     * The element's documentation body.
     *
     * @param element
     *            the element
     * @return the body, or an empty string when there is none. Never null
     */
    public String getDocumentation(Element element) {
        String body = "";
        Documentation documentation = this.firstDocumentation(element);
        if (documentation != null && documentation.getBody() != null) {
            body = documentation.getBody();
        }
        return body;
    }

    /**
     * Replaces the element's documentation body, creating it if there is none.
     *
     * @param element
     *            the element
     * @param body
     *            the new body; null is treated as empty
     * @return true when it was written
     */
    public boolean setDocumentation(Element element, String body) {
        String text = "";
        if (body != null) {
            text = body;
        }

        boolean written = false;
        if (element != null) {
            Documentation documentation = this.firstDocumentation(element);
            if (documentation == null) {
                documentation = SysmlFactory.eINSTANCE.createDocumentation();
                OwningMembership owningMembership = SysmlFactory.eINSTANCE.createOwningMembership();
                element.getOwnedRelationship().add(owningMembership);
                owningMembership.getOwnedRelatedElement().add(documentation);
            }
            documentation.setBody(text);
            written = true;
        }
        return written;
    }

    private Documentation firstDocumentation(Element element) {
        Documentation found = null;
        if (element != null) {
            found = element.getOwnedRelationship().stream()
                    .filter(OwningMembership.class::isInstance)
                    .map(OwningMembership.class::cast)
                    .flatMap(owningMembership -> owningMembership.getOwnedRelatedElement().stream())
                    .filter(Documentation.class::isInstance)
                    .map(Documentation.class::cast)
                    .findFirst()
                    .orElse(null);
        }
        return found;
    }
}
