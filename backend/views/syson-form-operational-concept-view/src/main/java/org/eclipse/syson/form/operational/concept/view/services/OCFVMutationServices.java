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
import org.eclipse.syson.sysml.MetadataUsage;
import org.eclipse.syson.sysml.OwningMembership;
import org.eclipse.syson.sysml.SysmlFactory;

/**
 * Writes the operational concept narrative back.
 *
 * <p>
 * The narrative is stored as documentation of the {@code OperationalConcept} annotation, leaving
 * the element's own {@code doc} free to say what the package is. See {@code OCFVQueryServices} for
 * why that separation exists and why a string attribute was rejected.
 * </p>
 *
 * @author DTPrendergast
 */
public class OCFVMutationServices {

    private final OCFVQueryServices queryServices = new OCFVQueryServices();

    /**
     * Sets the narrative, creating the {@code Documentation} on the annotation if there is none yet.
     *
     * <p>
     * This edits the existing {@code Documentation} in place rather than importing textual SysML.
     * Everywhere a <em>structure</em> is being created - a metadata annotation, a requirement - this
     * project goes through {@code ISysMLTextImporter} so the parser validates it. A documentation
     * body is different: it is free prose in a string attribute, there is no syntax for the parser
     * to check, and routing it through the importer would mean embedding arbitrary user text in a
     * SysML string literal, which cannot represent an unescaped quotation mark (plan.md item 3).
     * </p>
     *
     * @param element
     *            the element being edited
     * @param newValue
     *            the new narrative; null is treated as empty
     * @return the element, so the AQL expression has something to continue from
     */
    public Element setNarrative(Element element, String newValue) {
        String body = "";
        if (newValue != null) {
            body = newValue;
        }

        MetadataUsage annotation = this.queryServices.operationalConceptAnnotation(element);
        if (annotation == null) {
            // The form is only offered on elements that carry the annotation, so this means the
            // marker was removed while the editor was open. Writing the narrative onto the element
            // instead would put it somewhere nothing reads it back from, so nothing is written.
            return element;
        }

        Documentation documentation = this.firstDocumentation(annotation);
        if (documentation == null) {
            documentation = SysmlFactory.eINSTANCE.createDocumentation();
            OwningMembership owningMembership = SysmlFactory.eINSTANCE.createOwningMembership();
            annotation.getOwnedRelationship().add(owningMembership);
            owningMembership.getOwnedRelatedElement().add(documentation);
        }
        documentation.setBody(body);
        return element;
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
