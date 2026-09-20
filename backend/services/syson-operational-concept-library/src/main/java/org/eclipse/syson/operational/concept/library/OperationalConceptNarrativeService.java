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
import org.eclipse.syson.sysml.Relationship;
import org.eclipse.syson.sysml.SysmlFactory;
import org.eclipse.syson.sysml.TextualRepresentation;
import org.springframework.stereotype.Service;

/**
 * Reads and writes the narrative of an operational concept.
 *
 * <p>
 * The narrative is a {@link TextualRepresentation} named {@code conopsNarrative}, in language
 * {@code markdown}, owned by the operational concept itself. That is plain SysML v2 - it exports as
 * {@code rep conopsNarrative language "markdown" \/* … *\/} and any other v2 tool reads it - and
 * its meaning is exactly what it holds: a textual representation of the element, in a language the
 * model states rather than leaves implied.
 * </p>
 *
 * <p>
 * It used to live as documentation of the {@code @OperationalConcept} annotation. Two things were
 * wrong with that. The serializer drops applied metadata entirely, so a whole ConOps document
 * vanished on export without saying so. And the narrative then depended on a marker that looks like
 * incidental noise in the explorer - deleting what appeared to be a stray object destroyed the
 * document. Here, deleting the marker costs the operational-concept status, not the text.
 * </p>
 *
 * <p>
 * Content written under the old scheme is still read, and is moved on the next write.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
public class OperationalConceptNarrativeService {

    /** The declared name of the narrative, so it is distinguishable from any other representation. */
    public static final String NARRATIVE_NAME = "conopsNarrative";

    /** Declared on the element rather than assumed by a reader. */
    public static final String NARRATIVE_LANGUAGE = "markdown";

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
        TextualRepresentation representation = this.findNarrative(element);
        if (representation != null && representation.getBody() != null) {
            narrative = representation.getBody();
        } else {
            // Written under the previous scheme and not yet moved.
            Documentation legacy = this.legacyNarrative(element);
            if (legacy != null && legacy.getBody() != null) {
                narrative = legacy.getBody();
            }
        }
        return narrative;
    }

    /**
     * Replaces the narrative, creating the representation if there is none yet, and clearing out
     * any copy left by the previous storage scheme.
     *
     * <p>
     * Does nothing when the element is not an operational concept. Writing a narrative onto
     * something else would put it where nothing reads it back from.
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

        boolean written = false;
        if (this.findAnnotation(element) != null) {
            TextualRepresentation representation = this.findNarrative(element);
            if (representation == null) {
                representation = SysmlFactory.eINSTANCE.createTextualRepresentation();
                representation.setDeclaredName(NARRATIVE_NAME);
                representation.setLanguage(NARRATIVE_LANGUAGE);
                OwningMembership owningMembership = SysmlFactory.eINSTANCE.createOwningMembership();
                element.getOwnedRelationship().add(owningMembership);
                owningMembership.getOwnedRelatedElement().add(representation);
            }
            representation.setBody(body);
            this.removeLegacyNarrative(element);
            written = true;
        }
        return written;
    }

    private TextualRepresentation findNarrative(Element element) {
        TextualRepresentation found = null;
        if (element != null) {
            found = element.getOwnedRelationship().stream()
                    .filter(OwningMembership.class::isInstance)
                    .map(OwningMembership.class::cast)
                    .flatMap(owningMembership -> owningMembership.getOwnedRelatedElement().stream())
                    .filter(TextualRepresentation.class::isInstance)
                    .map(TextualRepresentation.class::cast)
                    .filter(representation -> NARRATIVE_NAME.equals(representation.getDeclaredName()))
                    .findFirst()
                    .orElse(null);
        }
        return found;
    }

    /**
     * The narrative as the previous scheme stored it: documentation of the annotation.
     */
    private Documentation legacyNarrative(Element element) {
        Documentation found = null;
        MetadataUsage annotation = this.findAnnotation(element);
        if (annotation != null) {
            found = annotation.getOwnedRelationship().stream()
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

    /**
     * Drops the old copy once the narrative has been written to its new home, so there is one
     * source of truth rather than two that can drift apart.
     */
    private void removeLegacyNarrative(Element element) {
        Documentation legacy = this.legacyNarrative(element);
        if (legacy != null) {
            MetadataUsage annotation = this.findAnnotation(element);
            Relationship membership = annotation.getOwnedRelationship().stream()
                    .filter(OwningMembership.class::isInstance)
                    .map(OwningMembership.class::cast)
                    .filter(owningMembership -> owningMembership.getOwnedRelatedElement().contains(legacy))
                    .findFirst()
                    .orElse(null);
            if (membership != null) {
                annotation.getOwnedRelationship().remove(membership);
            }
        }
    }
}
