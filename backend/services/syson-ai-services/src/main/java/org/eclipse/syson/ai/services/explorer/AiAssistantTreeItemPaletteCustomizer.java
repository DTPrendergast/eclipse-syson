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
import org.eclipse.syson.operational.concept.library.OperationalConceptNarrativeService;
import org.eclipse.syson.sysml.Element;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/**
 * Puts the assistant's tasks on the context menu of the element they apply to.
 *
 * <p>
 * The assistant used to be reached from the project menu, which meant it worked on whatever
 * happened to be selected in the explorer - and a selection is not always a model element. Select a
 * document node or a representation and the generation ran against nothing, which surfaced as the
 * model politely explaining it had not been given anything. Hanging the tasks off the element's own
 * menu removes the ambiguity: the entry knows which element it was opened on.
 * </p>
 *
 * <p>
 * Entries are appended to whatever palette the platform and SysON have already built, never
 * replacing it. Returning a fresh palette here would silently drop "New objects from text" and the
 * expression entries from every operational concept.
 * </p>
 *
 * <p>
 * <b>Ordered last, and that is required rather than tidy.</b> Customizers are chained, each handed
 * the previous one's palette - but {@code SysONExplorerTreeItemContextMenuEntryProvider} discards
 * what it is given and returns a palette built from scratch. Run before it and these entries are
 * silently thrown away, which is exactly what happened the first time this shipped. Running last
 * is the only position from which appending survives.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
@Order(Ordered.LOWEST_PRECEDENCE)
public class AiAssistantTreeItemPaletteCustomizer implements ITreeItemPaletteCustomizer {

    public static final String DRAFT_NARRATIVE_ENTRY_ID = "aiDraftNarrative";

    public static final String DRAFT_USE_CASES_ENTRY_ID = "aiDraftUseCases";

    private final IObjectSearchService objectSearchService;

    private final OperationalConceptNarrativeService narrativeService;

    public AiAssistantTreeItemPaletteCustomizer(IObjectSearchService objectSearchService, OperationalConceptNarrativeService narrativeService) {
        this.objectSearchService = Objects.requireNonNull(objectSearchService);
        this.narrativeService = Objects.requireNonNull(narrativeService);
    }

    @Override
    public boolean canHandle(IEditingContext editingContext, TreeDescription treeDescription, Tree tree, TreeItem treeItem) {
        return this.operationalConcept(editingContext, treeItem) != null;
    }

    @Override
    public Palette customize(IEditingContext editingContext, TreeDescription treeDescription, Tree tree, TreeItem treeItem, Palette palette) {
        List<IPaletteEntry> entries = new ArrayList<>(palette.paletteEntries());
        entries.add(new SingleClickTreeItemTool(DRAFT_NARRATIVE_ENTRY_ID, "", List.of(), false, List.of()));
        entries.add(new SingleClickTreeItemTool(DRAFT_USE_CASES_ENTRY_ID, "", List.of(), false, List.of()));
        return new Palette(palette.id(), palette.quickAccessTools(), entries);
    }

    /**
     * The operational concept a tree item points at, if it points at one.
     *
     * <p>
     * Uses the same marker test as the explorer icon and the narrative editor, so all three agree
     * about what counts as an operational concept.
     * </p>
     */
    private Element operationalConcept(IEditingContext editingContext, TreeItem treeItem) {
        return this.objectSearchService.getObject(editingContext, treeItem.getId())
                .filter(Element.class::isInstance)
                .map(Element.class::cast)
                .filter(element -> this.narrativeService.findAnnotation(element) != null)
                .orElse(null);
    }
}
