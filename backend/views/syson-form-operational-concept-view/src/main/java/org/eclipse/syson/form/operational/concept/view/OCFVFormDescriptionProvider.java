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
import org.eclipse.sirius.components.view.form.GroupDisplayMode;
import org.eclipse.syson.sysml.SysmlPackage;
import org.eclipse.syson.util.AQLConstants;
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
 * <b>Editing is plain text; rendering is separate and read-only.</b> The platform's rich-text
 * widget is a Lexical WYSIWYG editor, and its Markdown shortcuts stop firing after the first
 * transform - type {@code # } on a second line and the hash stays as literal text. That is an
 * upstream bug in {@code sirius-components-markdown}, not something this view can fix without
 * owning an editor component, so the narrative is authored in a textarea as raw Markdown where no
 * shortcut handling is involved at all, and the rendered form appears beneath it as a disabled
 * rich-text widget. A disabled widget renders without a toolbar and without an editable surface,
 * which is exactly the static rendered view wanted, and it uses no code we would have to maintain.
 * </p>
 *
 * <p>
 * The two are toggled rather than stacked. The platform's form renderer lays groups out itself and
 * offers no splitter, so a draggable divider is not reachable from the view DSL; what it does offer
 * is {@code TOGGLEABLE_AREAS}, which shows one widget of a group at a time and gives it the whole
 * area. Writing and reading a narrative are separate activities, so switching between them beats
 * splitting the panel anyway.
 * </p>
 *
 * <p>
 * The preview refreshes when the source loses focus, because that is when the form commits and
 * re-reads the value - so it updates at the end of an edit rather than on every keystroke.
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

        var source = this.formBuilders.newTextAreaDescription()
                .name("OCFV-TextArea-Narrative")
                .labelExpression("Source")
                .helpExpression("Who uses this system, and how do they intend to use it? Written as Markdown; the rendered form is below.")
                .valueExpression("aql:self.getDocumentationBody()")
                .body(this.viewBuilders.newChangeContext()
                        .expression("aql:self.setDocumentationBody(newValue)")
                        .build())
                .build();

        var rendered = this.formBuilders.newRichTextDescription()
                .name("OCFV-RichText-Preview")
                .labelExpression("Rendered")
                .valueExpression("aql:self.getDocumentationBody()")
                // Disabled on purpose, and this is the whole point of splitting the two: a disabled
                // rich-text widget renders Markdown with no toolbar and no editable surface, so
                // none of the editor's shortcut handling is in play.
                .isEnabledExpression(AQLConstants.AQL_FALSE)
                .build();

        // One group, not two. TOGGLEABLE_AREAS puts a toggle button on each *widget of a group*
        // and renders only the selected one, so a single group is what gives source and preview the
        // whole panel in turn. Two groups would stack two separate toggles instead.
        GroupDescription narrativeGroup = this.formBuilders.newGroupDescription()
                .name("OCFV-Group-Narrative")
                .labelExpression("Operational concept")
                .displayMode(GroupDisplayMode.TOGGLEABLE_AREAS)
                .semanticCandidatesExpression("aql:self")
                .children(source, rendered)
                .build();

        var page = this.formBuilders.newPageDescription()
                .name("OCFV-Page")
                .labelExpression("aql:self.name")
                .domainType(domainType)
                .semanticCandidatesExpression("aql:self")
                .groups(narrativeGroup)
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
