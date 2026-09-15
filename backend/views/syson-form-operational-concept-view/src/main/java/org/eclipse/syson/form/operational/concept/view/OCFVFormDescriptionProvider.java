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
package org.eclipse.syson.form.operational.concept.view;

import org.eclipse.sirius.components.view.RepresentationDescription;
import org.eclipse.sirius.components.view.builder.generated.form.FormBuilders;
import org.eclipse.sirius.components.view.builder.generated.view.ViewBuilders;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.builder.providers.IRepresentationDescriptionProvider;
import org.eclipse.sirius.components.view.form.GroupDescription;
import org.eclipse.syson.sysml.SysmlPackage;
import org.eclipse.syson.util.SysMLMetamodelHelper;

/**
 * The operational concept narrative, as an editable Markdown document in the main editor panel.
 *
 * <p>
 * An operational concept is mostly prose - who uses the system and how they intend to use it - and
 * a one-line text field in the Details panel is a poor place to write or read several paragraphs of
 * it. A form is a representation, so this opens in the main panel beside diagrams and tables, and
 * the platform's rich-text widget renders Markdown rather than showing raw characters.
 * </p>
 *
 * <p>
 * No new frontend code was needed: {@code RichTextPropertySection} already exists on the client and
 * already round-trips through an edit mutation. This declares the widget; the platform renders it.
 * </p>
 *
 * @author DTPrendergast
 */
public class OCFVFormDescriptionProvider implements IRepresentationDescriptionProvider {

    public static final String DESCRIPTION_NAME = "Operational Concept View";

    private final FormBuilders formBuilders = new FormBuilders();

    private final ViewBuilders viewBuilders = new ViewBuilders();

    @Override
    public RepresentationDescription create(IColorProvider colorProvider) {
        String domainType = SysMLMetamodelHelper.buildQualifiedName(SysmlPackage.eINSTANCE.getElement());

        var narrative = this.formBuilders.newRichTextDescription()
                .name("OCFV-RichText-Narrative")
                .labelExpression("Narrative")
                .helpExpression("Who uses this system, and how do they intend to use it? Markdown is rendered.")
                .valueExpression("aql:self.getDocumentationBody()")
                .body(this.viewBuilders.newChangeContext()
                        .expression("aql:self.setDocumentationBody(newValue)")
                        .build())
                .build();

        GroupDescription group = this.formBuilders.newGroupDescription()
                .name("OCFV-Group")
                .labelExpression("Operational concept")
                .semanticCandidatesExpression("aql:self")
                .children(narrative)
                .build();

        var page = this.formBuilders.newPageDescription()
                .name("OCFV-Page")
                .labelExpression("aql:self.name")
                .domainType(domainType)
                .semanticCandidatesExpression("aql:self")
                .groups(group)
                .build();

        return this.formBuilders.newFormDescription()
                .name(DESCRIPTION_NAME)
                .domainType(domainType)
                // Offered only on elements actually marked as operational concepts. The marker is
                // metadata rather than a type, so it cannot be expressed as a domain type and has
                // to be a precondition.
                .preconditionExpression("aql:self.isOperationalConcept()")
                .titleExpression("aql:'Operational Concept'")
                .pages(page)
                .build();
    }
}
