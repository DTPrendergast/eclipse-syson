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
package org.eclipse.syson.tree.metadata.filter;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.sirius.components.collaborative.trees.api.ITreeFilterProvider;
import org.eclipse.sirius.components.collaborative.trees.api.TreeFilter;
import org.eclipse.sirius.components.trees.description.TreeDescription;
import org.eclipse.syson.tree.explorer.filters.SysONTreeFilterConstants;
import org.eclipse.syson.tree.explorer.view.SysONExplorerTreeDescriptionProvider;
import org.springframework.stereotype.Service;

/**
 * Offers a "Hide Textual Representations" filter in the SysON explorer.
 *
 * <p>
 * A {@code rep} is where the text of a document is stored, not where anyone writes it: the ConOps
 * narrative is a {@code TextualRepresentation} named {@code conopsNarrative} and is authored
 * through the CONOPS Narrative View. Shown in the tree it is a second way into the same text, one
 * that offers no editor and whose body duplicates what the view already displays.
 * </p>
 *
 * <p>
 * This is a different filter from "Hide Metadata" rather than an extension of it, because the two
 * objects are different in a way that matters: the {@code @OperationalConcept} marker can be
 * deleted and cost only the operational-concept status, whereas deleting the representation
 * destroys the document. Folding a document into a filter named for metadata would blur exactly
 * the distinction a reader needs.
 * </p>
 *
 * <p>
 * Defaulted <b>on</b>: the text is reachable, and better reached, through its view. It stays a
 * filter so the representation can be seen when someone wants to confirm where the document
 * actually lives. As with "Hide Metadata", only the checkbox lives here - the hiding itself is a
 * patch to {@code SysONExplorerFilterService}, whose if-chain never consults the platform's
 * pluggable content providers.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
public class TextualRepresentationTreeFilterProvider implements ITreeFilterProvider {

    @Override
    public List<TreeFilter> get(String editingContextId, TreeDescription treeDescription) {
        List<TreeFilter> filters = new ArrayList<>();
        if (SysONExplorerTreeDescriptionProvider.SYSON_EXPLORER.equals(treeDescription.getLabel())) {
            filters.add(new TreeFilter(SysONTreeFilterConstants.HIDE_TEXTUAL_REPRESENTATIONS_TREE_FILTER_ID, "Hide Textual Representations", true));
        }
        return filters;
    }
}
