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
package org.eclipse.syson.ai.services.explorer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.sirius.components.collaborative.trees.dto.palette.SingleClickTreeItemTool;
import org.eclipse.sirius.components.collaborative.trees.palette.api.ITreeItemPaletteCustomizer;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IObjectSearchService;
import org.eclipse.sirius.components.palette.dto.IPaletteEntry;
import org.eclipse.sirius.components.palette.dto.Palette;
import org.eclipse.sirius.components.trees.Tree;
import org.eclipse.sirius.components.trees.TreeItem;
import org.eclipse.sirius.components.trees.description.TreeDescription;
import org.eclipse.sirius.web.domain.boundedcontexts.representationdata.RepresentationMetadata;
import org.eclipse.syson.operational.concept.library.OperationalConceptNarrativeService;
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.UseCaseUsage;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/**
 * Puts each assistant task on the context menu of the thing it works on.
 *
 * <p>
 * The narrative assistant hangs off a <em>representation</em> of an operational concept - the
 * CONOPS Narrative View - because that is where the narrative is read and written. The use case
 * assistant hangs off an individual {@code UseCaseUsage}, because it details one use case rather
 * than inventing the set of them.
 * </p>
 *
 * <p>
 * Reached from the project menu, the assistant worked on whatever happened to be selected in the
 * explorer, and a selection is not always a model element. An entry knows the tree item it was
 * opened on, so there is nothing left to infer.
 * </p>
 *
 * <p>
 * Entries are appended to whatever palette the platform and SysON have already built, never
 * replacing it, and this customizer is <b>ordered last</b> because
 * {@code SysONExplorerTreeItemContextMenuEntryProvider} discards the palette it is handed and
 * returns one built from scratch. Run before it and these entries are silently thrown away.
 * </p>
 *
 * <p>
 * Labels are set here rather than left to the frontend. SysON's own entries pass {@code ""} and
 * rely on their override component to draw the row, which means an entry whose override does not
 * match renders as a blank, clickable, invisible line.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
@Order(Ordered.LOWEST_PRECEDENCE)
public class AiAssistantTreeItemPaletteCustomizer implements ITreeItemPaletteCustomizer {

    public static final String NARRATIVE_ASSISTANT_ENTRY_ID = "aiNarrativeAssistant";

    public static final String USE_CASE_ASSISTANT_ENTRY_ID = "aiUseCaseAssistant";

    private final IObjectSearchService objectSearchService;

    private final OperationalConceptNarrativeService narrativeService;

    public AiAssistantTreeItemPaletteCustomizer(IObjectSearchService objectSearchService, OperationalConceptNarrativeService narrativeService) {
        this.objectSearchService = Objects.requireNonNull(objectSearchService);
        this.narrativeService = Objects.requireNonNull(narrativeService);
    }

    @Override
    public boolean canHandle(IEditingContext editingContext, TreeDescription treeDescription, Tree tree, TreeItem treeItem) {
        return this.isOperationalConceptRepresentation(editingContext, treeItem) || this.isUseCase(editingContext, treeItem);
    }

    @Override
    public Palette customize(IEditingContext editingContext, TreeDescription treeDescription, Tree tree, TreeItem treeItem, Palette palette) {
        List<IPaletteEntry> entries = new ArrayList<>(palette.paletteEntries());
        if (this.isOperationalConceptRepresentation(editingContext, treeItem)) {
            entries.add(new SingleClickTreeItemTool(NARRATIVE_ASSISTANT_ENTRY_ID, "Narrative AI Assistant", List.of(), false, List.of()));
        }
        if (this.isUseCase(editingContext, treeItem)) {
            entries.add(new SingleClickTreeItemTool(USE_CASE_ASSISTANT_ENTRY_ID, "Use Case AI Assistant", List.of(), false, List.of()));
        }
        return new Palette(palette.id(), palette.quickAccessTools(), entries);
    }

    /**
     * Whether this tree item is a representation of an operational concept.
     *
     * <p>
     * Matched on what the representation targets rather than on which description produced it. A
     * description id would be the narrower test, but it would mean this module depending on the
     * view module, and those ids change when a view is renamed - which already orphaned one
     * representation in this project. The cost of the looser test is that the entry also appears on
     * any other representation of the same operational concept, which is harmless.
     * </p>
     */
    private boolean isOperationalConceptRepresentation(IEditingContext editingContext, TreeItem treeItem) {
        return this.objectSearchService.getObject(editingContext, treeItem.getId())
                .filter(RepresentationMetadata.class::isInstance)
                .map(RepresentationMetadata.class::cast)
                .flatMap(metadata -> this.objectSearchService.getObject(editingContext, metadata.getTargetObjectId()))
                .filter(Element.class::isInstance)
                .map(Element.class::cast)
                .filter(element -> this.narrativeService.findAnnotation(element) != null)
                .isPresent();
    }

    private boolean isUseCase(IEditingContext editingContext, TreeItem treeItem) {
        return this.objectSearchService.getObject(editingContext, treeItem.getId())
                .filter(UseCaseUsage.class::isInstance)
                .isPresent();
    }
}
