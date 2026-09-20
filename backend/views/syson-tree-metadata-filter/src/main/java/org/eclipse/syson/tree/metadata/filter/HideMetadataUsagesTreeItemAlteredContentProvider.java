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

import java.util.List;

import org.eclipse.sirius.components.representations.VariableManager;
import org.eclipse.sirius.web.application.views.explorer.services.api.IExplorerTreeItemAlteredContentProvider;
import org.eclipse.syson.sysml.MetadataUsage;
import org.springframework.stereotype.Service;

/**
 * Drops metadata usages from the explorer when "Hide Metadata" is active.
 *
 * <p>
 * Only the metadata usage itself is removed. Anything it owns goes with it, which is the intended
 * behaviour here and worth being explicit about: an operational concept's narrative used to live
 * inside its marker, and hiding the marker would have hidden the narrative too. It is stored on the
 * concept itself now, so there is nothing of the user's underneath.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
public class HideMetadataUsagesTreeItemAlteredContentProvider implements IExplorerTreeItemAlteredContentProvider {

    @Override
    public boolean canHandle(Object object, List<String> activeFilterIds) {
        return activeFilterIds.contains(MetadataTreeFilterConstants.HIDE_METADATA_USAGES_TREE_FILTER_ID);
    }

    @Override
    public List<Object> apply(List<Object> computedChildren, VariableManager variableManager) {
        return computedChildren.stream()
                .filter(child -> !(child instanceof MetadataUsage))
                .toList();
    }
}
