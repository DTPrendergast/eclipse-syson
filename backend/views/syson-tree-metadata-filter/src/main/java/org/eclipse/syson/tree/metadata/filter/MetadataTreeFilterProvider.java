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
 * Offers a "Hide Metadata" filter in the SysON explorer.
 *
 * <p>
 * Applied metadata shows up in the tree as an element of its own, which is structurally correct and
 * practically noise: a marker such as the operational concept's {@code @OperationalConcept}, a
 * requirement's attributes, or an AI provenance stamp reads as a stray object next to the content
 * it annotates. One user asked what a metadata usage in their tree was and whether deleting it
 * would break anything - a reasonable question, and one with an expensive answer.
 * </p>
 *
 * <p>
 * Defaulted <b>on</b>, like SysON's own "Hide Memberships", because the same argument applies: it
 * is structure rather than content. It stays a filter rather than an unconditional rule so the
 * marker can still be seen when someone needs to confirm it is there.
 * </p>
 *
 * <p>
 * Only the checkbox lives here. The platform genuinely does collect every
 * {@link ITreeFilterProvider} - the Studio explorer ships one alongside SysON's - so declaring a
 * filter needs no upstream edit. Acting on it does: the SysON explorer computes its children
 * through {@code SysONExplorerFilterService.applyFilters}, a fixed if-chain over SysON's own filter
 * ids that never consults the platform's {@code IExplorerTreeItemAlteredContentProvider} beans. A
 * provider registered that way is simply never called, which is how this filter first shipped -
 * visible, toggleable, and doing nothing. The hiding is therefore a small patch to that service,
 * and the id is taken from SysON's constants so the two cannot drift.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
public class MetadataTreeFilterProvider implements ITreeFilterProvider {

    @Override
    public List<TreeFilter> get(String editingContextId, TreeDescription treeDescription) {
        List<TreeFilter> filters = new ArrayList<>();
        if (SysONExplorerTreeDescriptionProvider.SYSON_EXPLORER.equals(treeDescription.getLabel())) {
            filters.add(new TreeFilter(SysONTreeFilterConstants.HIDE_METADATA_USAGES_TREE_FILTER_ID, "Hide Metadata", true));
        }
        return filters;
    }
}
