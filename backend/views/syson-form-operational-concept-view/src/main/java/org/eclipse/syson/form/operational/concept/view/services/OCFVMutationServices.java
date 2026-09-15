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
import org.eclipse.syson.sysml.SysmlFactory;

/**
 * Writes the documentation body of an element back, for the operational concept form.
 *
 * @author DTPrendergast
 */
public class OCFVMutationServices {

    /**
     * Sets the element's documentation body, creating the {@code Documentation} if the element does
     * not have one yet.
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
     *            the new body; null is treated as an empty body
     * @return the element, so the AQL expression has something to continue from
     */
    public Element setDocumentationBody(Element element, String newValue) {
        String body = "";
        if (newValue != null) {
            body = newValue;
        }

        Documentation documentation = this.firstDocumentation(element);
        if (documentation == null) {
            documentation = SysmlFactory.eINSTANCE.createDocumentation();
            OwningMembership owningMembership = SysmlFactory.eINSTANCE.createOwningMembership();
            element.getOwnedRelationship().add(owningMembership);
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
