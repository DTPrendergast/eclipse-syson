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
package org.eclipse.syson.operational.concept.library;

import org.eclipse.syson.sysml.Documentation;
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.MetadataUsage;
import org.eclipse.syson.sysml.OwningMembership;
import org.eclipse.syson.sysml.SysmlFactory;
import org.springframework.stereotype.Service;

/**
 * Reads and writes the narrative of an operational concept.
 *
 * <p>
 * It lives in this module, next to the metadata definition it depends on, because two very
 * different callers need it: the Operational Concept View, where a person edits the narrative, and
 * the AI assistant, which drafts one. Duplicating the traversal in both would be two places to get
 * the storage shape wrong.
 * </p>
 *
 * <p>
 * The narrative is documentation of the {@code OperationalConcept} annotation, not of the element
 * itself. A package's own {@code doc} says what the package is; the narrative says who uses the
 * system and how. Keeping them apart means editing one never disturbs the other.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
public class OperationalConceptNarrativeService {

    /**
     * The {@code OperationalConcept} annotation on an element, if it carries one.
     *
     * @param element
     *            the candidate element
     * @return the annotation, or null when the element is not an operational concept
     */
    public MetadataUsage findAnnotation(Element element) {
        MetadataUsage found = null;
        if (element != null) {
            found = element.getOwnedRelationship().stream()
                    .filter(OwningMembership.class::isInstance)
                    .map(OwningMembership.class::cast)
                    .flatMap(owningMembership -> owningMembership.getOwnedRelatedElement().stream())
                    .filter(MetadataUsage.class::isInstance)
                    .map(MetadataUsage.class::cast)
                    .filter(metadataUsage -> metadataUsage.getMetadataDefinition() != null
                            && "OperationalConcept".equals(metadataUsage.getMetadataDefinition().getName()))
                    .findFirst()
                    .orElse(null);
        }
        return found;
    }

    /**
     * The narrative.
     *
     * @param element
     *            the operational concept
     * @return the narrative, or an empty string when there is none. Never null: callers bind this
     *         straight to an editor value, where null shows up as a broken widget
     */
    public String getNarrative(Element element) {
        String narrative = "";
        Documentation documentation = this.narrativeDocumentation(element);
        if (documentation != null && documentation.getBody() != null) {
            narrative = documentation.getBody();
        }
        return narrative;
    }

    /**
     * Replaces the narrative, creating the {@code Documentation} if there is none yet.
     *
     * <p>
     * Does nothing when the element carries no annotation. Falling back to the element's own
     * documentation would put the narrative where nothing reads it back from, and would overwrite
     * whatever that documentation said.
     * </p>
     *
     * @param element
     *            the operational concept
     * @param narrative
     *            the new narrative; null is treated as empty
     * @return true when the narrative was written
     */
    public boolean setNarrative(Element element, String narrative) {
        String body = "";
        if (narrative != null) {
            body = narrative;
        }

        MetadataUsage annotation = this.findAnnotation(element);
        boolean written = false;
        if (annotation != null) {
            Documentation documentation = this.firstDocumentation(annotation);
            if (documentation == null) {
                documentation = SysmlFactory.eINSTANCE.createDocumentation();
                OwningMembership owningMembership = SysmlFactory.eINSTANCE.createOwningMembership();
                annotation.getOwnedRelationship().add(owningMembership);
                owningMembership.getOwnedRelatedElement().add(documentation);
            }
            documentation.setBody(body);
            written = true;
        }
        return written;
    }

    private Documentation narrativeDocumentation(Element element) {
        Documentation documentation = null;
        MetadataUsage annotation = this.findAnnotation(element);
        if (annotation != null) {
            documentation = this.firstDocumentation(annotation);
        }
        return documentation;
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
