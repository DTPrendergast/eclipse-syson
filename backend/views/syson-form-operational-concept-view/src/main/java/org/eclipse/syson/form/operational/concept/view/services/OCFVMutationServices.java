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
import org.eclipse.syson.sysml.Element;

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

    private final OperationalConceptNarrativeService narrativeService = new OperationalConceptNarrativeService();

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

        // Returns false when the marker was removed while the editor was open; nothing is written
        // in that case, deliberately.
        this.narrativeService.setNarrative(element, body);
        return element;
    }

}
