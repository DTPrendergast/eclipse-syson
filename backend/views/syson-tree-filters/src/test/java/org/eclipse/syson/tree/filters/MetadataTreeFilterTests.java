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
package org.eclipse.syson.tree.filters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.eclipse.sirius.components.trees.description.TreeDescription;
import org.eclipse.syson.tree.explorer.view.SysONExplorerTreeDescriptionProvider;
import org.junit.jupiter.api.Test;

/**
 * Tests of the "Hide Metadata" explorer filter.
 *
 * @author DTPrendergast
 */
class MetadataTreeFilterTests {

    private final MetadataTreeFilterProvider filterProvider = new MetadataTreeFilterProvider();

    @Test
    void offersTheFilterOnTheSysonExplorerAndDefaultsItOn() {
        var filters = this.filterProvider.get("editing-context", this.treeDescription(SysONExplorerTreeDescriptionProvider.SYSON_EXPLORER));

        assertThat(filters).hasSize(1);
        assertThat(filters.get(0).label()).isEqualTo("Hide Metadata");
        // On by default: a marker reads as a stray object next to the content it annotates, and one
        // was mistaken for cruft worth deleting.
        assertThat(filters.get(0).defaultState()).isTrue();
    }

    @Test
    void leavesOtherTreesAlone() {
        assertThat(this.filterProvider.get("editing-context", this.treeDescription("Some Other Explorer"))).isEmpty();
    }

    private TreeDescription treeDescription(String label) {
        // Mocked rather than built: the filter reads only the label, and TreeDescription's builder
        // demands a dozen providers that would say nothing about what is being tested.
        TreeDescription treeDescription = mock(TreeDescription.class);
        when(treeDescription.getLabel()).thenReturn(label);
        return treeDescription;
    }
}
